# Autenticação e Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Adicionar login por conta e checagem de posse (ownership) ao Ponte, para que cada conta só acesse o(s) perfil(is) de criança que possui.

**Architecture:** Spring Security com sessão por cookie (não JWT), um `Account` por responsável dono de um `ChildProfile` (FK simples `accountId`, sem `@ManyToOne`, seguindo o padrão já usado em `ConsentRecord.childProfileId`). Checagem de ownership vive nos controllers via um `ChildOwnershipGuard` compartilhado, antes de delegar para os services — os services continuam sem conhecer conta nenhuma.

**Tech Stack:** Java 17, Spring Boot 3.4, Spring Security 6 (via `spring-boot-starter-security`), Spring Data JPA, H2, MockMvc + `spring-security-test`.

## Global Constraints

- Sem `@ManyToOne`/relações JPA — FKs são campos `Long` simples (convenção já usada em todo o domínio).
- Sem setters em entidades — construtor + getters, igual às entidades existentes.
- CSRF desabilitado (decisão explícita do spec: sem CORS configurado, sem superfície cross-origin).
- Papel único de conta (sem RBAC), sem cadastro de múltiplos perfis de criança por conta, sem verificação de e-mail/recuperação de senha — tudo isso fica fora desta fatia (ver spec, seção "Fora de escopo").
- Conta demo do seed: e-mail `demo@ponte.app`, senha `ponte1234`.
- Spec completo em `docs/superpowers/specs/2026-07-20-auth-ownership-design.md` — consulte para o "porquê" de qualquer decisão não repetida aqui.

---

## Task 1: Dependência do Spring Security + entidade `Account`

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/br/com/ponte/account/Account.java`
- Create: `src/main/java/br/com/ponte/account/AccountRepository.java`
- Create: `src/main/java/br/com/ponte/config/SecurityConfig.java`
- Test: `src/test/java/br/com/ponte/account/AccountRepositoryTest.java`

**Interfaces:**
- Produces: `Account(String email, String passwordHash)` construtor; `Account.getId(): Long`, `getEmail(): String`, `getPasswordHash(): String`, `getCreatedAt(): Instant`. `AccountRepository extends JpaRepository<Account, Long>` com `findByEmail(String email): Optional<Account>`. `SecurityConfig` com bean `SecurityFilterChain filterChain(HttpSecurity)` — próximas tasks vão **modificar** este bean, não recriar.

- [ ] **Step 1: Adicionar dependências ao `pom.xml`**

Adicione estas duas dependências dentro de `<dependencies>` (depois de `spring-boot-starter-validation`, antes do H2):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
```

E depois de `spring-boot-starter-test` (com escopo `test`):

```xml
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

- [ ] **Step 2: Criar um `SecurityConfig` permissivo AGORA, antes de qualquer outra coisa**

**Importante:** assim que `spring-boot-starter-security` entra no classpath, o
Spring Boot auto-configura uma trava total (usuário `user` com senha
aleatória gerada no log, autenticação exigida em toda rota) enquanto não
existir um `SecurityFilterChain` próprio. Sem este passo, a suíte de testes
inteira (`SymbolApiTest`, `UsageApiTest`, `PredictionApiTest`,
`CustomSymbolApiTest`, `ProfileConsentApiTest` — nenhuma delas tocada nesta
task) passaria a falhar com 401 silenciosamente. Este `SecurityConfig` é só
um espaço reservado permissivo; as tasks seguintes vão apertá-lo aos poucos.

Crie `src/main/java/br/com/ponte/config/SecurityConfig.java`:

```java
package br.com.ponte.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Espaço reservado: libera tudo por enquanto. As próximas tasks apertam
 * as regras de autenticação e ownership progressivamente.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

- [ ] **Step 3: Rodar a suíte inteira e confirmar que nada quebrou**

Run: `mvn -q test`
Expected: PASS — as mesmas 19 classes/testes de antes, agora com o
Spring Security no classpath mas sem nenhuma rota travada.

- [ ] **Step 4: Escrever o teste (falhando) do `AccountRepository`**

Crie `src/test/java/br/com/ponte/account/AccountRepositoryTest.java`:

```java
package br.com.ponte.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AccountRepositoryTest {

    @Autowired AccountRepository accounts;

    @Test
    void encontraContaPeloEmail() {
        accounts.save(new Account("bia@ponte.app", "hash"));

        assertThat(accounts.findByEmail("bia@ponte.app")).isPresent();
        assertThat(accounts.findByEmail("outro@ponte.app")).isEmpty();
    }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que falha (classes `Account`/`AccountRepository` não existem)**

Run: `mvn -q test -Dtest=AccountRepositoryTest`
Expected: FAIL — erro de compilação, "cannot find symbol: class Account" (ou `AccountRepository`).

- [ ] **Step 6: Criar `Account`**

Crie `src/main/java/br/com/ponte/account/Account.java`:

```java
package br.com.ponte.account;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Conta do responsável/cuidador que possui um ou mais ChildProfile.
 * Papel único nesta fatia — sem RBAC.
 */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {}

    public Account(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 7: Criar `AccountRepository`**

Crie `src/main/java/br/com/ponte/account/AccountRepository.java`:

```java
package br.com.ponte.account;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmail(String email);
}
```

- [ ] **Step 8: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=AccountRepositoryTest`
Expected: PASS.

- [ ] **Step 9: Rodar a suíte inteira de novo (confirmação final desta task)**

Run: `mvn -q test`
Expected: PASS — 20 testes agora (19 de antes + `encontraContaPeloEmail`).

- [ ] **Step 10: Commit**

```bash
git add pom.xml src/main/java/br/com/ponte/account/ src/main/java/br/com/ponte/config/SecurityConfig.java src/test/java/br/com/ponte/account/
git commit -m "feat: entidade Account, dependência do Spring Security e SecurityConfig permissivo"
```

---

## Task 2: `ChildProfile.accountId` + conta demo no seed

**Files:**
- Modify: `src/main/java/br/com/ponte/profile/ChildProfile.java`
- Modify: `src/main/java/br/com/ponte/profile/ChildProfileRepository.java`
- Modify: `src/main/java/br/com/ponte/config/DataSeeder.java`
- Modify: `src/test/java/br/com/ponte/profile/ProfileConsentApiTest.java`
- Modify: `src/test/java/br/com/ponte/usage/UsageApiTest.java`
- Test: `src/test/java/br/com/ponte/profile/ChildProfileRepositoryTest.java`

**Interfaces:**
- Consumes: `Account(String email, String passwordHash)`, `AccountRepository` (Task 1).
- Produces: `ChildProfile(String displayName, Long accountId)` construtor (assinatura muda — quebra todo call site existente, corrigido nesta mesma task); `ChildProfile.getAccountId(): Long`. `ChildProfileRepository.findByAccountId(Long): List<ChildProfile>`, `.existsByIdAndAccountId(Long, Long): boolean`.

- [ ] **Step 1: Escrever o teste (falhando) do `ChildProfileRepository`**

Crie `src/test/java/br/com/ponte/profile/ChildProfileRepositoryTest.java`:

```java
package br.com.ponte.profile;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ChildProfileRepositoryTest {

    @Autowired ChildProfileRepository profiles;
    @Autowired AccountRepository accounts;

    @Test
    void filtraPerfisPelaContaDona() {
        Account dono = accounts.save(new Account("dono@ponte.app", "hash"));
        Account outraConta = accounts.save(new Account("outra@ponte.app", "hash"));
        ChildProfile deDono = profiles.save(new ChildProfile("Bia", dono.getId()));
        profiles.save(new ChildProfile("Outra criança", outraConta.getId()));

        assertThat(profiles.findByAccountId(dono.getId())).containsExactly(deDono);
        assertThat(profiles.existsByIdAndAccountId(deDono.getId(), dono.getId())).isTrue();
        assertThat(profiles.existsByIdAndAccountId(deDono.getId(), outraConta.getId())).isFalse();
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=ChildProfileRepositoryTest`
Expected: FAIL — erro de compilação (`ChildProfile(String, Long)` não existe; `findByAccountId`/`existsByIdAndAccountId` não existem).

- [ ] **Step 3: Adicionar `accountId` a `ChildProfile`**

Substitua o conteúdo de `src/main/java/br/com/ponte/profile/ChildProfile.java`:

```java
package br.com.ponte.profile;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Perfil da criança. LGPD: displayName é um apelido — nunca armazenar
 * nome completo, documento ou dado que identifique a criança diretamente.
 */
@Entity
public class ChildProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private Long accountId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected ChildProfile() {}

    public ChildProfile(String displayName, Long accountId) {
        this.displayName = displayName;
        this.accountId = accountId;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Long getAccountId() { return accountId; }
    public Instant getCreatedAt() { return createdAt; }
}
```

- [ ] **Step 4: Adicionar os métodos derivados a `ChildProfileRepository`**

Substitua o conteúdo de `src/main/java/br/com/ponte/profile/ChildProfileRepository.java`:

```java
package br.com.ponte.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChildProfileRepository extends JpaRepository<ChildProfile, Long> {
    List<ChildProfile> findByAccountId(Long accountId);
    boolean existsByIdAndAccountId(Long id, Long accountId);
}
```

- [ ] **Step 5: Rodar o teste do repositório e confirmar que passa**

Run: `mvn -q test -Dtest=ChildProfileRepositoryTest`
Expected: PASS.

- [ ] **Step 6: Corrigir `DataSeeder` para criar a conta demo dona do perfil**

O `ChildProfile` agora exige `accountId` no construtor — o `DataSeeder`
não compila mais até criar a conta antes do perfil. Substitua o conteúdo
de `src/main/java/br/com/ponte/config/DataSeeder.java`:

```java
package br.com.ponte.config;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.consent.ConsentRecord;
import br.com.ponte.consent.ConsentRecordRepository;
import br.com.ponte.profile.ChildProfile;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.ImageType;
import br.com.ponte.symbol.SymbolCategory;
import br.com.ponte.symbol.SymbolService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import static br.com.ponte.symbol.SymbolCategory.*;

/**
 * Seed do MVP: uma conta demo (demo@ponte.app / ponte1234) dona de um
 * perfil com consentimento e a prancha padrão (16 símbolos emoji) — sem
 * dependência de API externa.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final AccountRepository accounts;
    private final ChildProfileRepository profiles;
    private final ConsentRecordRepository consents;
    private final SymbolService symbolService;

    public DataSeeder(AccountRepository accounts, ChildProfileRepository profiles,
                      ConsentRecordRepository consents, SymbolService symbolService) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.consents = consents;
        this.symbolService = symbolService;
    }

    @Override
    public void run(String... args) {
        if (profiles.count() > 0) {
            return;
        }
        Account demoAccount = accounts.save(new Account(
                "demo@ponte.app", new BCryptPasswordEncoder().encode("ponte1234")));
        ChildProfile demo = profiles.save(new ChildProfile("Alex", demoAccount.getId()));
        consents.save(new ConsentRecord(demo.getId(), "Responsável demo",
                "Registro de uso da prancha para acompanhamento terapêutico"));

        // símbolos globais (childId null): posições 0..15, nesta ordem
        seed(COMIDA, "maçã", "🍎");
        seed(COMIDA, "banana", "🍌");
        seed(COMIDA, "água", "💧");
        seed(COMIDA, "biscoito", "🍪");
        seed(SENTIMENTOS, "feliz", "😊");
        seed(SENTIMENTOS, "triste", "😢");
        seed(SENTIMENTOS, "bravo", "😠");
        seed(SENTIMENTOS, "cansado", "😴");
        seed(PESSOAS, "eu", "🙋");
        seed(PESSOAS, "mamãe", "👩");
        seed(PESSOAS, "papai", "👨");
        seed(PESSOAS, "professora", "🧑‍🏫");
        seed(ACOES, "quero", "🤲");
        seed(ACOES, "comer", "🍽️");
        seed(ACOES, "brincar", "🧸");
        seed(ACOES, "parar", "🛑");
    }

    private void seed(SymbolCategory category, String label, String emoji) {
        symbolService.addSymbol(null, label, category, ImageType.EMOJI, emoji);
    }
}
```

- [ ] **Step 7: Corrigir os dois testes que criam `ChildProfile` diretamente**

Estes dois arquivos chamam `new ChildProfile("Nome")` — o construtor
antigo não existe mais. Em `src/test/java/br/com/ponte/profile/ProfileConsentApiTest.java`,
adicione os imports e o helper, e troque as 4 ocorrências:

```java
import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
```

(junto aos imports existentes, em ordem alfabética antes de
`br.com.ponte.consent.ConsentRecordRepository`), adicione também:

```java
import java.util.UUID;
```

Adicione o campo e o helper dentro da classe, logo após os `@Autowired`
existentes:

```java
    @Autowired AccountRepository accounts;

    private Long newAccountId() {
        return accounts.save(new Account(UUID.randomUUID() + "@ponte.app", "hash")).getId();
    }
```

E troque as 4 ocorrências de `new ChildProfile("Bia")` por
`new ChildProfile("Bia", newAccountId())` (uma em cada método de teste:
`listaPerfisComStatusDeConsentimento`, `registraConsentimento`,
`revogaConsentimento`, `consentimentoSemResponsavelEhRejeitado`).

Em `src/test/java/br/com/ponte/usage/UsageApiTest.java`, adicione os
imports:

```java
import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
```

adicione o campo e helper (mesmo padrão):

```java
    @Autowired AccountRepository accounts;

    private Long newAccountId() {
        return accounts.save(new Account(java.util.UUID.randomUUID() + "@ponte.app", "hash")).getId();
    }
```

E troque, no teste `rejeitaEventoSemConsentimento`, a linha:

```java
        ChildProfile semConsentimento = profiles.save(new ChildProfile("Novo"));
```

por:

```java
        ChildProfile semConsentimento = profiles.save(new ChildProfile("Novo", newAccountId()));
```

- [ ] **Step 8: Rodar a suíte inteira e confirmar que compila e passa**

Run: `mvn -q test`
Expected: PASS — 21 testes (20 de antes + `filtraPerfisPelaContaDona`).

- [ ] **Step 9: Commit**

```bash
git add src/main/java/br/com/ponte/profile/ src/main/java/br/com/ponte/config/DataSeeder.java src/test/java/br/com/ponte/profile/ src/test/java/br/com/ponte/usage/UsageApiTest.java
git commit -m "feat: ChildProfile ganha accountId; seed cria conta demo dona do perfil"
```

---

## Task 3: `AccountPrincipal` + `AccountUserDetailsService` + `PasswordEncoder`

**Files:**
- Create: `src/main/java/br/com/ponte/account/AccountPrincipal.java`
- Create: `src/main/java/br/com/ponte/account/AccountUserDetailsService.java`
- Modify: `src/main/java/br/com/ponte/config/SecurityConfig.java`
- Test: `src/test/java/br/com/ponte/account/AccountUserDetailsServiceTest.java`

**Interfaces:**
- Consumes: `Account`, `AccountRepository` (Task 1).
- Produces: `AccountPrincipal implements UserDetails`, construtor `AccountPrincipal(Account)`, `getAccount(): Account`, `getAccountId(): Long`. `AccountUserDetailsService implements UserDetailsService`. Bean `PasswordEncoder` (Spring-managed, `BCryptPasswordEncoder`) — Task 4 (`AuthController`) injeta esse bean em vez de instanciar um novo.

- [ ] **Step 1: Escrever o teste (falhando) de `AccountUserDetailsService`**

Crie `src/test/java/br/com/ponte/account/AccountUserDetailsServiceTest.java`:

```java
package br.com.ponte.account;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountUserDetailsServiceTest {

    private final AccountRepository accounts = mock(AccountRepository.class);
    private final AccountUserDetailsService service = new AccountUserDetailsService(accounts);

    @Test
    void carregaContaExistentePeloEmail() {
        Account account = new Account("bia@ponte.app", "hash");
        when(accounts.findByEmail("bia@ponte.app")).thenReturn(Optional.of(account));

        AccountPrincipal principal = (AccountPrincipal) service.loadUserByUsername("bia@ponte.app");

        assertThat(principal.getAccount()).isSameAs(account);
    }

    @Test
    void lancaExcecaoQuandoContaNaoExiste() {
        when(accounts.findByEmail("fantasma@ponte.app")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("fantasma@ponte.app"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=AccountUserDetailsServiceTest`
Expected: FAIL — erro de compilação (`AccountPrincipal`/`AccountUserDetailsService` não existem).

- [ ] **Step 3: Criar `AccountPrincipal`**

Crie `src/main/java/br/com/ponte/account/AccountPrincipal.java`:

```java
package br.com.ponte.account;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Papel único nesta fatia — sem authorities/RBAC. */
public class AccountPrincipal implements UserDetails {

    private final Account account;

    public AccountPrincipal(Account account) {
        this.account = account;
    }

    public Account getAccount() { return account; }
    public Long getAccountId() { return account.getId(); }

    @Override public String getUsername() { return account.getEmail(); }
    @Override public String getPassword() { return account.getPasswordHash(); }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(); }
}
```

- [ ] **Step 4: Criar `AccountUserDetailsService`**

Crie `src/main/java/br/com/ponte/account/AccountUserDetailsService.java`:

```java
package br.com.ponte.account;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accounts;

    public AccountUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        Account account = accounts.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Conta não encontrada."));
        return new AccountPrincipal(account);
    }
}
```

- [ ] **Step 5: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=AccountUserDetailsServiceTest`
Expected: PASS.

- [ ] **Step 6: Adicionar o bean `PasswordEncoder` ao `SecurityConfig`**

Substitua o conteúdo de `src/main/java/br/com/ponte/config/SecurityConfig.java`:

```java
package br.com.ponte.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ainda libera tudo (`anyRequest().permitAll()`) — a Task 5 aperta para
 * exigir autenticação em `/api/v1/**` (exceto register/login).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
```

- [ ] **Step 7: Rodar a suíte inteira e confirmar que nada quebrou**

Run: `mvn -q test`
Expected: PASS — 23 testes (21 de antes + 2 de `AccountUserDetailsServiceTest`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/ponte/account/ src/main/java/br/com/ponte/config/SecurityConfig.java src/test/java/br/com/ponte/account/AccountUserDetailsServiceTest.java
git commit -m "feat: AccountPrincipal, AccountUserDetailsService e PasswordEncoder"
```

---

## Task 4: `AuthController` (register/login/logout/me)

**Files:**
- Create: `src/main/java/br/com/ponte/account/InvalidCredentialsException.java`
- Create: `src/main/java/br/com/ponte/account/AuthController.java`
- Modify: `src/main/java/br/com/ponte/config/ApiExceptionHandler.java`
- Test: `src/test/java/br/com/ponte/account/AuthApiTest.java`

**Interfaces:**
- Consumes: `AccountRepository`, `PasswordEncoder` (Tasks 1/3). `HttpServletRequest.login(String, String)`/`.logout()` (Servlet API, funciona automaticamente com `AccountUserDetailsService` + `PasswordEncoder` já registrados — nenhum bean `AuthenticationManager` extra é necessário).
- Produces: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `POST /api/v1/auth/logout`, `GET /api/v1/auth/me`. Nesta task o `SecurityConfig` ainda libera tudo (Task 3), então `/me`/`/logout` não estão de fato protegidos por Spring Security ainda — só o comportamento funcional (login cria sessão, logout encerra) é testado aqui. A Task 5 é quem liga a exigência real de autenticação.

- [ ] **Step 1: Escrever o teste (falhando) de `AuthController`**

Crie `src/test/java/br/com/ponte/account/AuthApiTest.java`:

```java
package br.com.ponte.account;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    @Autowired MockMvc mvc;

    @Test
    void cadastraLogaEAcessaMe() throws Exception {
        MvcResult registerResult = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "nova@ponte.app", "password": "senha1234"}
                    """))
           .andExpect(status().isCreated())
           .andReturn();

        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);

        mvc.perform(get("/api/v1/auth/me").session(session))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("nova@ponte.app"));
    }

    @Test
    void loginComSenhaErradaEhRejeitado() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "erra@ponte.app", "password": "senha1234"}
                    """))
           .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "erra@ponte.app", "password": "senhaerrada"}
                    """))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutEncerraASessao() throws Exception {
        MvcResult registerResult = mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "sai@ponte.app", "password": "senha1234"}
                    """))
           .andExpect(status().isCreated())
           .andReturn();
        MockHttpSession session = (MockHttpSession) registerResult.getRequest().getSession(false);

        mvc.perform(post("/api/v1/auth/logout").session(session))
           .andExpect(status().isNoContent());
    }

    @Test
    void emailJaCadastradoEhRejeitado() throws Exception {
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "duplicado@ponte.app", "password": "senha1234"}
                    """))
           .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email": "duplicado@ponte.app", "password": "outrasenha"}
                    """))
           .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=AuthApiTest`
Expected: FAIL — erro de compilação (não existe `/api/v1/auth/*` ainda).

- [ ] **Step 3: Criar `InvalidCredentialsException`**

Crie `src/main/java/br/com/ponte/account/InvalidCredentialsException.java`:

```java
package br.com.ponte.account;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("E-mail ou senha inválidos.");
    }
}
```

- [ ] **Step 4: Mapear a exceção para 401 em `ApiExceptionHandler`**

Em `src/main/java/br/com/ponte/config/ApiExceptionHandler.java`, adicione o
import `br.com.ponte.account.InvalidCredentialsException` (junto ao
import existente de `ConsentRequiredException`) e este método (por
exemplo, logo após `consentRequired`):

```java
    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> invalidCredentials(InvalidCredentialsException ex) {
        return Map.of("error", ex.getMessage());
    }
```

- [ ] **Step 5: Criar `AuthController`**

Crie `src/main/java/br/com/ponte/account/AuthController.java`:

```java
package br.com.ponte.account;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    public record RegisterRequest(@NotBlank @Email String email, @NotBlank @Size(min = 8) String password) {}
    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    public AuthController(AccountRepository accounts, PasswordEncoder passwordEncoder) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest)
            throws ServletException {
        if (accounts.findByEmail(request.email()).isPresent()) {
            throw new IllegalArgumentException("E-mail já cadastrado.");
        }
        accounts.save(new Account(request.email(), passwordEncoder.encode(request.password())));
        httpRequest.login(request.email(), request.password());
    }

    @PostMapping("/login")
    public void login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            httpRequest.login(request.email(), request.password());
        } catch (ServletException e) {
            throw new InvalidCredentialsException();
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) throws ServletException {
        httpRequest.logout();
    }

    @GetMapping("/me")
    public Map<String, String> me(@AuthenticationPrincipal AccountPrincipal principal) {
        return Map.of("email", principal.getAccount().getEmail());
    }
}
```

- [ ] **Step 6: Rodar o teste e confirmar que passa**

Run: `mvn -q test -Dtest=AuthApiTest`
Expected: PASS.

- [ ] **Step 7: Rodar a suíte inteira**

Run: `mvn -q test`
Expected: PASS — 27 testes (23 de antes + 4 de `AuthApiTest`).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/br/com/ponte/account/InvalidCredentialsException.java src/main/java/br/com/ponte/account/AuthController.java src/main/java/br/com/ponte/config/ApiExceptionHandler.java src/test/java/br/com/ponte/account/AuthApiTest.java
git commit -m "feat: AuthController (register/login/logout/me)"
```

---

## Task 5: Ligar a autenticação de verdade + corrigir a suíte existente

**Esta é a task de maior raio de impacto do plano.** Até aqui o
`SecurityConfig` liberava tudo — nada quebrava. Este passo aperta
`/api/v1/**` para exigir sessão (exceto `/auth/register` e `/auth/login`),
o que derruba imediatamente `CustomSymbolApiTest`, `ProfileConsentApiTest`,
`UsageApiTest`, `SymbolApiTest` e `PredictionApiTest` com `401` — nenhuma
delas autentica hoje. Corrigir todas elas faz parte desta mesma task, para
o repositório nunca ficar num commit com suíte quebrada.

**Files:**
- Modify: `src/main/java/br/com/ponte/config/SecurityConfig.java`
- Create: `src/test/java/br/com/ponte/testsupport/TestAuth.java`
- Modify: `src/test/java/br/com/ponte/account/AuthApiTest.java`
- Modify: `src/test/java/br/com/ponte/picto/CustomSymbolApiTest.java`
- Modify: `src/test/java/br/com/ponte/symbol/SymbolApiTest.java`
- Modify: `src/test/java/br/com/ponte/prediction/PredictionApiTest.java`
- Modify: `src/test/java/br/com/ponte/profile/ProfileConsentApiTest.java`
- Modify: `src/test/java/br/com/ponte/usage/UsageApiTest.java`

**Interfaces:**
- Consumes: `AccountPrincipal` (Task 3), `AccountRepository` (Task 1).
- Produces: `TestAuth.as(Account): RequestPostProcessor` — helper de teste compartilhado, usado com `.with(TestAuth.as(account))` em qualquer chamada `MockMvc` que precise estar autenticada como uma conta específica.

- [ ] **Step 1: Apertar o `SecurityConfig` para exigir autenticação**

Substitua o conteúdo de `src/main/java/br/com/ponte/config/SecurityConfig.java`:

```java
package br.com.ponte.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Sem formLogin/httpBasic: uma rota autenticada sem sessão recebe 401 puro. */
    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> response.sendError(401);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll())
            .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint()));
        return http.build();
    }
}
```

- [ ] **Step 2: Rodar a suíte inteira e confirmar a quebra esperada**

Run: `mvn -q test`
Expected: FAIL — `CustomSymbolApiTest`, `ProfileConsentApiTest`, `UsageApiTest`,
`SymbolApiTest` e `PredictionApiTest` falham com `401` em vez do status
esperado. `AuthApiTest` continua passando (só usa rotas `permitAll` ou
sessões já autenticadas pelo próprio login).

- [ ] **Step 3: Criar o helper de teste compartilhado `TestAuth`**

Crie `src/test/java/br/com/ponte/testsupport/TestAuth.java`:

```java
package br.com.ponte.testsupport;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountPrincipal;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Autentica uma requisição MockMvc como a conta dada, sem passar pelo fluxo real de login. */
public final class TestAuth {

    private TestAuth() {}

    public static RequestPostProcessor as(Account account) {
        return SecurityMockMvcRequestPostProcessors.user(new AccountPrincipal(account));
    }
}
```

- [ ] **Step 4: Corrigir `CustomSymbolApiTest`**

Substitua o conteúdo de `src/test/java/br/com/ponte/picto/CustomSymbolApiTest.java`:

```java
package br.com.ponte.picto;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CustomSymbolApiTest {

    @TempDir
    static Path tempUploads;

    @DynamicPropertySource
    static void uploadsDir(DynamicPropertyRegistry registry) {
        registry.add("ponte.uploads.dir", () -> tempUploads.toString());
        // banco próprio: este contexto forka com ddl-auto=create e derrubaria
        // o schema do H2 nomeado compartilhado com os outros contextos de teste
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:ponte-picto;DB_CLOSE_DELAY=-1");
    }

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;
    @Autowired AccountRepository accounts;

    private RequestPostProcessor asDemoAccount() {
        Account demo = accounts.findByEmail("demo@ponte.app").orElseThrow();
        return TestAuth.as(demo);
    }

    private byte[] fakePhoto() throws Exception {
        BufferedImage img = new BufferedImage(100, 60, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private byte[] fakeOversizedPhoto() throws Exception {
        // 1px de altura: dimensão só precisa exceder o limite num dos lados,
        // não precisa alocar muita memória para reproduzir o teste
        BufferedImage img = new BufferedImage(8001, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void fotoViraSimboloNoFimDaPranchaSemMoverOsExistentes() throws Exception {
        Long childId = profiles.findAll().get(0).getId();
        List<Symbol> before = symbols.boardFor(childId);

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "dino.png", "image/png", fakePhoto()))
                .param("childId", childId.toString())
                .param("label", "dino")
                .with(asDemoAccount()))
           .andExpect(status().isCreated())
           .andExpect(jsonPath("$.label").value("dino"))
           .andExpect(jsonPath("$.category").value("PERSONALIZADO"))
           .andExpect(jsonPath("$.imageType").value("AI_GENERATED"))
           .andExpect(jsonPath("$.gridPosition").value(before.size()));

        List<Symbol> after = symbols.boardFor(childId);
        assertThat(after).hasSize(before.size() + 1);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).getId()).isEqualTo(before.get(i).getId());
        }

        // o stub gravou o pictograma 512x512 no diretório de uploads
        String imageRef = after.get(after.size() - 1).getImageRef();
        assertThat(imageRef).startsWith("/uploads/");
        Path saved = tempUploads.resolve(imageRef.substring("/uploads/".length()));
        assertThat(Files.exists(saved)).isTrue();
        BufferedImage generated = ImageIO.read(saved.toFile());
        assertThat(generated.getWidth()).isEqualTo(512);
        assertThat(generated.getHeight()).isEqualTo(512);
    }

    @Test
    void arquivoQueNaoEhImagemEhRejeitado() throws Exception {
        Long childId = profiles.findAll().get(0).getId();

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "x.txt", "text/plain", "nada".getBytes()))
                .param("childId", childId.toString())
                .param("label", "coisa")
                .with(asDemoAccount()))
           .andExpect(status().isBadRequest());
    }

    @Test
    void fotoComDimensoesAcimaDoLimiteEhRejeitada() throws Exception {
        Long childId = profiles.findAll().get(0).getId();

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "gigante.png", "image/png", fakeOversizedPhoto()))
                .param("childId", childId.toString())
                .param("label", "gigante")
                .with(asDemoAccount()))
           .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 5: Rodar `CustomSymbolApiTest` sozinho e confirmar que passa**

Run: `mvn -q test -Dtest=CustomSymbolApiTest`
Expected: PASS (3 testes).

- [ ] **Step 6: Commit parcial**

```bash
git add src/main/java/br/com/ponte/config/SecurityConfig.java src/test/java/br/com/ponte/testsupport/ src/test/java/br/com/ponte/picto/CustomSymbolApiTest.java
git commit -m "feat: SecurityConfig exige autenticação em /api/v1/**; corrige CustomSymbolApiTest"
```

- [ ] **Step 7: Corrigir `SymbolApiTest`**

Só o teste `seedCriaPranchaComDezesseisSimbolosOrdenados` chama `MockMvc`
(os outros dois chamam `symbolService.addSymbol(...)` direto, sem HTTP, e
não precisam de autenticação). Substitua o conteúdo de
`src/test/java/br/com/ponte/symbol/SymbolApiTest.java`:

```java
package br.com.ponte.symbol;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SymbolApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;
    @Autowired SymbolService symbolService;
    @Autowired AccountRepository accounts;

    private Long seedChildId() {
        return profiles.findAll().get(0).getId();
    }

    @Test
    void seedCriaPranchaComDezesseisSimbolosOrdenados() throws Exception {
        Account demo = accounts.findByEmail("demo@ponte.app").orElseThrow();

        mvc.perform(get("/api/v1/symbols").param("childId", seedChildId().toString())
                .with(TestAuth.as(demo)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(16))
           .andExpect(jsonPath("$[0].gridPosition").value(0))
           .andExpect(jsonPath("$[15].gridPosition").value(15))
           .andExpect(jsonPath("$[0].label").value("maçã"));
    }

    @Test
    void adicionarSimboloNuncaMoveOsExistentes() {
        Long childId = seedChildId();
        List<Symbol> before = symbols.boardFor(childId);

        Symbol added = symbolService.addSymbol(
                childId, "tablet", SymbolCategory.PERSONALIZADO, ImageType.EMOJI, "📱");

        // novo símbolo entra SEMPRE no fim (motor planning)
        assertThat(added.getGridPosition()).isEqualTo(before.size());

        List<Symbol> after = symbols.boardFor(childId);
        assertThat(after).hasSize(before.size() + 1);
        for (int i = 0; i < before.size(); i++) {
            assertThat(after.get(i).getId()).isEqualTo(before.get(i).getId());
            assertThat(after.get(i).getGridPosition()).isEqualTo(before.get(i).getGridPosition());
        }
    }

    @Test
    void simboloPersonalizadoDeOutraCriancaNaoApareceNaPrancha() {
        Long childId = seedChildId();
        symbolService.addSymbol(childId + 999, "outro", SymbolCategory.PERSONALIZADO, ImageType.EMOJI, "❓");

        List<Symbol> board = symbols.boardFor(childId);
        assertThat(board).noneMatch(s -> "outro".equals(s.getLabel()));
    }
}
```

- [ ] **Step 8: Corrigir `PredictionApiTest`**

Substitua o conteúdo de `src/test/java/br/com/ponte/prediction/PredictionApiTest.java`:

```java
package br.com.ponte.prediction;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PredictionApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;
    @Autowired AccountRepository accounts;

    @Test
    void sugereFraseAPartirDosToques() throws Exception {
        Long childId = profiles.findAll().get(0).getId();
        Long quero = idByLabel(childId, "quero");
        Long maca = idByLabel(childId, "maçã");
        Account demo = accounts.findByEmail("demo@ponte.app").orElseThrow();

        mvc.perform(post("/api/v1/predictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolIds": [%d, %d]}
                    """.formatted(childId, quero, maca))
                .with(TestAuth.as(demo)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.suggestions[0]").value("Eu quero comer maçã"));
    }

    private Long idByLabel(Long childId, String label) {
        return symbols.boardFor(childId).stream()
                .filter(s -> s.getLabel().equals(label))
                .findFirst().orElseThrow(() -> new IllegalStateException(label)).getId();
    }
}
```

- [ ] **Step 9: Rodar os dois testes e confirmar que passam**

Run: `mvn -q test -Dtest=SymbolApiTest,PredictionApiTest`
Expected: PASS (5 testes: 3 de `SymbolApiTest` + 1 de `PredictionApiTest`... confirme a contagem exibida, o importante é 0 falhas).

- [ ] **Step 10: Commit parcial**

```bash
git add src/test/java/br/com/ponte/symbol/SymbolApiTest.java src/test/java/br/com/ponte/prediction/PredictionApiTest.java
git commit -m "fix: autentica SymbolApiTest e PredictionApiTest como a conta demo"
```

- [ ] **Step 11: Corrigir `ProfileConsentApiTest`**

O helper `newAccountId()` (criado na Task 2) só devolvia o `Long` do id
— agora cada teste também precisa do `Account` inteiro para autenticar a
chamada `MockMvc`. Substitua o conteúdo de
`src/test/java/br/com/ponte/profile/ProfileConsentApiTest.java`:

```java
package br.com.ponte.profile;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.consent.ConsentRecordRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProfileConsentApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired ConsentRecordRepository consents;
    @Autowired AccountRepository accounts;

    private Account newAccount() {
        return accounts.save(new Account(UUID.randomUUID() + "@ponte.app", "hash"));
    }

    @Test
    void listaPerfisComStatusDeConsentimento() throws Exception {
        Account dono = newAccount();
        ChildProfile p = profiles.save(new ChildProfile("Bia", dono.getId()));

        mvc.perform(get("/api/v1/profiles").with(TestAuth.as(dono)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$[?(@.id == %d)].displayName".formatted(p.getId())).value("Bia"))
           .andExpect(jsonPath("$[?(@.id == %d)].hasActiveConsent".formatted(p.getId())).value(false));
    }

    @Test
    void registraConsentimento() throws Exception {
        Account dono = newAccount();
        ChildProfile p = profiles.save(new ChildProfile("Bia", dono.getId()));

        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "Maria", "purpose": "Acompanhamento terapêutico"}
                    """)
                .with(TestAuth.as(dono)))
           .andExpect(status().isCreated());

        assertThat(consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())).isTrue();
    }

    @Test
    void revogaConsentimento() throws Exception {
        Account dono = newAccount();
        ChildProfile p = profiles.save(new ChildProfile("Bia", dono.getId()));
        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "Maria", "purpose": "Acompanhamento terapêutico"}
                    """)
                .with(TestAuth.as(dono)))
           .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/profiles/{id}/consent/revoke", p.getId()).with(TestAuth.as(dono)))
           .andExpect(status().isNoContent());

        assertThat(consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())).isFalse();
    }

    @Test
    void consentimentoSemResponsavelEhRejeitado() throws Exception {
        Account dono = newAccount();
        ChildProfile p = profiles.save(new ChildProfile("Bia", dono.getId()));

        mvc.perform(post("/api/v1/profiles/{id}/consent", p.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "", "purpose": "x"}
                    """)
                .with(TestAuth.as(dono)))
           .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 12: Corrigir `UsageApiTest`**

Substitua o conteúdo de `src/test/java/br/com/ponte/usage/UsageApiTest.java`:

```java
package br.com.ponte.usage;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.profile.ChildProfile;
import br.com.ponte.profile.ChildProfileRepository;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UsageApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired SymbolRepository symbols;
    @Autowired AccountRepository accounts;

    private Long seedChildId() {
        return profiles.findAll().get(0).getId();
    }

    private RequestPostProcessor asDemoAccount() {
        return TestAuth.as(accounts.findByEmail("demo@ponte.app").orElseThrow());
    }

    private Account newAccount() {
        return accounts.save(new Account(UUID.randomUUID() + "@ponte.app", "hash"));
    }

    private Symbol symbolByLabel(String label) {
        List<Symbol> board = symbols.boardFor(seedChildId());
        return board.stream().filter(s -> s.getLabel().equals(label)).findFirst().orElseThrow();
    }

    private void tap(Long childId, Long symbolId, RequestPostProcessor auth) throws Exception {
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(childId, symbolId))
                .with(auth))
           .andExpect(status().isCreated());
    }

    @Test
    void registraToqueComConsentimentoAtivo() throws Exception {
        tap(seedChildId(), symbolByLabel("maçã").getId(), asDemoAccount());
    }

    @Test
    void rejeitaEventoSemConsentimento() throws Exception {
        Account dono = newAccount();
        ChildProfile semConsentimento = profiles.save(new ChildProfile("Novo", dono.getId()));

        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(semConsentimento.getId(), symbolByLabel("maçã").getId()))
                .with(TestAuth.as(dono)))
           .andExpect(status().isForbidden())
           .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void toqueSemSymbolIdEhRejeitado() throws Exception {
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "eventType": "SYMBOL_TAP"}
                    """.formatted(seedChildId()))
                .with(asDemoAccount()))
           .andExpect(status().isBadRequest());
    }

    @Test
    void resumoDoDiaContaToquesFrasesETopSimbolos() throws Exception {
        Long childId = seedChildId();
        Long maca = symbolByLabel("maçã").getId();
        Long banana = symbolByLabel("banana").getId();

        tap(childId, maca, asDemoAccount());
        tap(childId, maca, asDemoAccount());
        tap(childId, banana, asDemoAccount());
        mvc.perform(post("/api/v1/usage-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "eventType": "SENTENCE_SPOKEN"}
                    """.formatted(childId))
                .with(asDemoAccount()))
           .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/usage/summary").param("childId", childId.toString())
                .with(asDemoAccount()))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.totalTaps").value(3))
           .andExpect(jsonPath("$.sentencesSpoken").value(1))
           .andExpect(jsonPath("$.predictionsAccepted").value(0))
           .andExpect(jsonPath("$.topSymbols[0].label").value("maçã"))
           .andExpect(jsonPath("$.topSymbols[0].count").value(2))
           .andExpect(jsonPath("$.topSymbols[0].category").value("COMIDA"));
    }
}
```

- [ ] **Step 13: Rodar os dois testes e confirmar que passam**

Run: `mvn -q test -Dtest=ProfileConsentApiTest,UsageApiTest`
Expected: PASS (8 testes: 4 de `ProfileConsentApiTest` + 4 de `UsageApiTest`).

- [ ] **Step 14: Commit parcial**

```bash
git add src/test/java/br/com/ponte/profile/ProfileConsentApiTest.java src/test/java/br/com/ponte/usage/UsageApiTest.java
git commit -m "fix: autentica ProfileConsentApiTest e UsageApiTest com contas próprias"
```

- [ ] **Step 15: Adicionar o teste de rejeição sem sessão em `AuthApiTest`**

Só agora (com o `SecurityConfig` apertado) faz sentido testar que uma
rota protegida sem sessão nenhuma recebe `401`. Adicione este método a
`src/test/java/br/com/ponte/account/AuthApiTest.java`, dentro da classe,
depois de `emailJaCadastradoEhRejeitado`:

```java

    @Test
    void endpointProtegidoSemSessaoRetorna401() throws Exception {
        mvc.perform(get("/api/v1/profiles"))
           .andExpect(status().isUnauthorized());
    }
```

- [ ] **Step 16: Rodar a suíte inteira e confirmar que tudo passa**

Run: `mvn -q test`
Expected: PASS — 28 testes (27 de antes + `endpointProtegidoSemSessaoRetorna401`).

- [ ] **Step 17: Commit**

```bash
git add src/test/java/br/com/ponte/account/AuthApiTest.java
git commit -m "test: cobre rejeição 401 em rota protegida sem sessão"
```

---

## Task 6: `ChildOwnershipGuard` nos controllers

**Files:**
- Create: `src/main/java/br/com/ponte/profile/ChildProfileNotFoundException.java`
- Create: `src/main/java/br/com/ponte/profile/ChildOwnershipGuard.java`
- Modify: `src/main/java/br/com/ponte/config/ApiExceptionHandler.java`
- Modify: `src/main/java/br/com/ponte/profile/ProfileController.java`
- Modify: `src/main/java/br/com/ponte/symbol/SymbolController.java`
- Modify: `src/main/java/br/com/ponte/usage/UsageController.java`
- Modify: `src/main/java/br/com/ponte/prediction/PredictionController.java`
- Modify: `src/main/java/br/com/ponte/consent/ConsentController.java`
- Test: `src/test/java/br/com/ponte/profile/OwnershipApiTest.java`

**Interfaces:**
- Consumes: `ChildProfileRepository.existsByIdAndAccountId` (Task 2), `AccountPrincipal.getAccountId()` (Task 3).
- Produces: `ChildOwnershipGuard.requireOwned(Long childId, Long accountId): void` (lança `ChildProfileNotFoundException`, mapeada para `404`). Cada controller injeta esse bean e chama `requireOwned(...)` como primeira linha do método, antes de qualquer outra lógica.

- [ ] **Step 1: Escrever o teste (falhando) de ownership**

Crie `src/test/java/br/com/ponte/profile/OwnershipApiTest.java`:

```java
package br.com.ponte.profile;

import br.com.ponte.account.Account;
import br.com.ponte.account.AccountRepository;
import br.com.ponte.testsupport.TestAuth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OwnershipApiTest {

    @Autowired MockMvc mvc;
    @Autowired ChildProfileRepository profiles;
    @Autowired AccountRepository accounts;

    private Account newAccount() {
        return accounts.save(new Account(UUID.randomUUID() + "@ponte.app", "hash"));
    }

    private byte[] fakePng() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    void perfilDeOutraContaNaoApareceNaLista() throws Exception {
        Account contaA = newAccount();
        profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(get("/api/v1/profiles").with(TestAuth.as(contaB)))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void simbolosDeOutraContaRetornam404() throws Exception {
        Account contaA = newAccount();
        ChildProfile deA = profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(get("/api/v1/symbols").param("childId", deA.getId().toString())
                .with(TestAuth.as(contaB)))
           .andExpect(status().isNotFound());
    }

    @Test
    void adicionarSimboloParaCriancaDeOutraContaRetorna404() throws Exception {
        Account contaA = newAccount();
        ChildProfile deA = profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(multipart("/api/v1/symbols/custom")
                .file(new MockMultipartFile("photo", "x.png", "image/png", fakePng()))
                .param("childId", deA.getId().toString())
                .param("label", "novo")
                .with(TestAuth.as(contaB)))
           .andExpect(status().isNotFound());
    }

    @Test
    void resumoDeUsoDeOutraContaRetorna404() throws Exception {
        Account contaA = newAccount();
        ChildProfile deA = profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(get("/api/v1/usage/summary").param("childId", deA.getId().toString())
                .with(TestAuth.as(contaB)))
           .andExpect(status().isNotFound());
    }

    @Test
    void predicaoParaCriancaDeOutraContaRetorna404() throws Exception {
        Account contaA = newAccount();
        ChildProfile deA = profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(post("/api/v1/predictions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"childId": %d, "symbolIds": [999]}
                    """.formatted(deA.getId()))
                .with(TestAuth.as(contaB)))
           .andExpect(status().isNotFound());
    }

    @Test
    void consentimentoDeOutraContaRetorna404() throws Exception {
        Account contaA = newAccount();
        ChildProfile deA = profiles.save(new ChildProfile("Da conta A", contaA.getId()));
        Account contaB = newAccount();

        mvc.perform(post("/api/v1/profiles/{id}/consent", deA.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"guardianName": "Alguém", "purpose": "x"}
                    """)
                .with(TestAuth.as(contaB)))
           .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Rodar o teste e confirmar que falha**

Run: `mvn -q test -Dtest=OwnershipApiTest`
Expected: FAIL — hoje `GET /api/v1/profiles` já filtra corretamente por
conta (então o 1º teste passa), mas os outros recebem `200`/`201`
em vez de `404` (nenhum controller checa ownership ainda).

- [ ] **Step 3: Criar `ChildProfileNotFoundException`**

Crie `src/main/java/br/com/ponte/profile/ChildProfileNotFoundException.java`:

```java
package br.com.ponte.profile;

public class ChildProfileNotFoundException extends RuntimeException {
    public ChildProfileNotFoundException() {
        super("Perfil não encontrado.");
    }
}
```

- [ ] **Step 4: Criar `ChildOwnershipGuard`**

Crie `src/main/java/br/com/ponte/profile/ChildOwnershipGuard.java`:

```java
package br.com.ponte.profile;

import org.springframework.stereotype.Component;

/**
 * "Não existe" e "existe mas não é seu" respondem o mesmo 404 — de
 * propósito, para não revelar a outras contas que um perfil existe.
 */
@Component
public class ChildOwnershipGuard {

    private final ChildProfileRepository profiles;

    public ChildOwnershipGuard(ChildProfileRepository profiles) {
        this.profiles = profiles;
    }

    public void requireOwned(Long childId, Long accountId) {
        if (!profiles.existsByIdAndAccountId(childId, accountId)) {
            throw new ChildProfileNotFoundException();
        }
    }
}
```

- [ ] **Step 5: Mapear a exceção para 404 em `ApiExceptionHandler`**

Em `src/main/java/br/com/ponte/config/ApiExceptionHandler.java`, adicione
o import `br.com.ponte.profile.ChildProfileNotFoundException` e este
método:

```java
    @ExceptionHandler(ChildProfileNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> childProfileNotFound(ChildProfileNotFoundException ex) {
        return Map.of("error", ex.getMessage());
    }
```

- [ ] **Step 6: Filtrar `ProfileController.list` pela conta**

Substitua o conteúdo de `src/main/java/br/com/ponte/profile/ProfileController.java`:

```java
package br.com.ponte.profile;

import br.com.ponte.account.AccountPrincipal;
import br.com.ponte.consent.ConsentRecordRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/profiles")
public class ProfileController {

    public record ProfileResponse(Long id, String displayName, boolean hasActiveConsent) {}

    private final ChildProfileRepository profiles;
    private final ConsentRecordRepository consents;

    public ProfileController(ChildProfileRepository profiles, ConsentRecordRepository consents) {
        this.profiles = profiles;
        this.consents = consents;
    }

    @GetMapping
    public List<ProfileResponse> list(@AuthenticationPrincipal AccountPrincipal principal) {
        return profiles.findByAccountId(principal.getAccountId()).stream()
                .map(p -> new ProfileResponse(
                        p.getId(),
                        p.getDisplayName(),
                        consents.existsByChildProfileIdAndRevokedAtIsNull(p.getId())))
                .toList();
    }
}
```

- [ ] **Step 7: Adicionar o guard a `SymbolController`**

Substitua o conteúdo de `src/main/java/br/com/ponte/symbol/SymbolController.java`:

```java
package br.com.ponte.symbol;

import br.com.ponte.account.AccountPrincipal;
import br.com.ponte.picto.PictogramGenerationService;
import br.com.ponte.profile.ChildOwnershipGuard;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/symbols")
public class SymbolController {

    public record SymbolResponse(Long id, String label, String category,
                                 String imageType, String imageRef, int gridPosition) {
        static SymbolResponse from(Symbol s) {
            return new SymbolResponse(s.getId(), s.getLabel(), s.getCategory().name(),
                    s.getImageType().name(), s.getImageRef(), s.getGridPosition());
        }
    }

    private final SymbolService symbolService;
    private final PictogramGenerationService pictograms;
    private final ChildOwnershipGuard ownership;

    public SymbolController(SymbolService symbolService, PictogramGenerationService pictograms,
                            ChildOwnershipGuard ownership) {
        this.symbolService = symbolService;
        this.pictograms = pictograms;
        this.ownership = ownership;
    }

    @GetMapping
    public List<SymbolResponse> board(@RequestParam Long childId,
                                      @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(childId, principal.getAccountId());
        return symbolService.boardFor(childId).stream().map(SymbolResponse::from).toList();
    }

    /**
     * Foto do universo da criança → pictograma (stub/IA) → símbolo novo
     * SEMPRE no fim da grade (motor planning: nunca reorganiza).
     */
    @PostMapping(value = "/custom", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SymbolResponse addCustom(@RequestParam Long childId,
                                    @RequestParam String label,
                                    @RequestParam(defaultValue = "PERSONALIZADO") SymbolCategory category,
                                    @RequestPart("photo") MultipartFile photo,
                                    @AuthenticationPrincipal AccountPrincipal principal) throws IOException {
        ownership.requireOwned(childId, principal.getAccountId());
        String imageRef = pictograms.generate(photo.getBytes(), label);
        Symbol symbol = symbolService.addSymbol(childId, label, category, ImageType.AI_GENERATED, imageRef);
        return SymbolResponse.from(symbol);
    }
}
```

- [ ] **Step 8: Adicionar o guard a `UsageController`**

Substitua o conteúdo de `src/main/java/br/com/ponte/usage/UsageController.java`:

```java
package br.com.ponte.usage;

import br.com.ponte.account.AccountPrincipal;
import br.com.ponte.profile.ChildOwnershipGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class UsageController {

    public record UsageEventRequest(
            @NotNull Long childId,
            Long symbolId,
            @NotNull UsageEventType eventType,
            Instant occurredAt) {}

    private final UsageService usageService;
    private final ChildOwnershipGuard ownership;

    public UsageController(UsageService usageService, ChildOwnershipGuard ownership) {
        this.usageService = usageService;
        this.ownership = ownership;
    }

    @PostMapping("/usage-events")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Long> record(@Valid @RequestBody UsageEventRequest request,
                                    @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(request.childId(), principal.getAccountId());
        UsageEvent saved = usageService.record(
                request.childId(), request.symbolId(), request.eventType(), request.occurredAt());
        return Map.of("id", saved.getId());
    }

    @GetMapping("/usage/summary")
    public UsageSummaryResponse summary(
            @RequestParam Long childId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(childId, principal.getAccountId());
        return usageService.summary(childId, date != null ? date : LocalDate.now(UsageService.ZONE));
    }
}
```

- [ ] **Step 9: Adicionar o guard a `PredictionController`**

Substitua o conteúdo de `src/main/java/br/com/ponte/prediction/PredictionController.java`:

```java
package br.com.ponte.prediction;

import br.com.ponte.account.AccountPrincipal;
import br.com.ponte.profile.ChildOwnershipGuard;
import br.com.ponte.symbol.Symbol;
import br.com.ponte.symbol.SymbolRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/predictions")
public class PredictionController {

    public record PredictionRequest(@NotNull Long childId, @NotEmpty List<Long> symbolIds) {}
    public record PredictionResponse(List<String> suggestions) {}

    private final SymbolRepository symbols;
    private final SentencePredictionService predictionService;
    private final ChildOwnershipGuard ownership;

    public PredictionController(SymbolRepository symbols, SentencePredictionService predictionService,
                                ChildOwnershipGuard ownership) {
        this.symbols = symbols;
        this.predictionService = predictionService;
        this.ownership = ownership;
    }

    @PostMapping
    public PredictionResponse predict(@Valid @RequestBody PredictionRequest request,
                                      @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(request.childId(), principal.getAccountId());
        Map<Long, Symbol> byId = symbols.findAllById(request.symbolIds()).stream()
                .collect(Collectors.toMap(Symbol::getId, Function.identity()));
        // preserva a ordem em que a criança tocou os símbolos
        List<Symbol> sequence = request.symbolIds().stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .toList();
        return new PredictionResponse(predictionService.predict(sequence));
    }
}
```

- [ ] **Step 10: Adicionar o guard a `ConsentController`**

Substitua o conteúdo de `src/main/java/br/com/ponte/consent/ConsentController.java`:

```java
package br.com.ponte.consent;

import br.com.ponte.account.AccountPrincipal;
import br.com.ponte.profile.ChildOwnershipGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/profiles/{profileId}/consent")
public class ConsentController {

    public record ConsentRequest(@NotBlank String guardianName, @NotBlank String purpose) {}

    private final ConsentRecordRepository consents;
    private final ChildOwnershipGuard ownership;

    public ConsentController(ConsentRecordRepository consents, ChildOwnershipGuard ownership) {
        this.consents = consents;
        this.ownership = ownership;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void grant(@PathVariable Long profileId, @Valid @RequestBody ConsentRequest request,
                      @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(profileId, principal.getAccountId());
        consents.save(new ConsentRecord(profileId, request.guardianName(), request.purpose()));
    }

    @PostMapping("/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void revoke(@PathVariable Long profileId, @AuthenticationPrincipal AccountPrincipal principal) {
        ownership.requireOwned(profileId, principal.getAccountId());
        consents.findByChildProfileIdAndRevokedAtIsNull(profileId)
                .forEach(ConsentRecord::revoke);
    }
}
```

- [ ] **Step 11: Rodar a suíte inteira e confirmar que tudo passa**

Run: `mvn -q test`
Expected: PASS — 34 testes (28 de antes + 6 de `OwnershipApiTest`).

- [ ] **Step 12: Commit**

```bash
git add src/main/java/br/com/ponte/profile/ src/main/java/br/com/ponte/config/ApiExceptionHandler.java src/main/java/br/com/ponte/symbol/SymbolController.java src/main/java/br/com/ponte/usage/UsageController.java src/main/java/br/com/ponte/prediction/PredictionController.java src/main/java/br/com/ponte/consent/ConsentController.java src/test/java/br/com/ponte/profile/OwnershipApiTest.java
git commit -m "feat: ChildOwnershipGuard em todos os controllers que recebem childId"
```

---

## Task 7: Frontend — login, redirect em 401, logout

Não há framework de teste JS neste projeto (o gate de consentimento
anterior também foi verificado só manualmente no navegador) — esta task
é verificada rodando o app de verdade, não com testes automatizados.

**Files:**
- Create: `src/main/resources/static/login.html`
- Create: `src/main/resources/static/login.js`
- Modify: `src/main/resources/static/styles.css`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/app.js`
- Modify: `src/main/resources/static/dashboard.html`
- Modify: `src/main/resources/static/dashboard.js`
- Modify: `src/main/resources/static/sw.js`

**Interfaces:**
- Consumes: `POST /api/v1/auth/login`, `POST /api/v1/auth/register`, `POST /api/v1/auth/logout`, `GET /api/v1/profiles` (retorna `401` sem sessão — Task 5).

- [ ] **Step 1: Criar `login.html`**

Crie `src/main/resources/static/login.html`:

```html
<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta name="theme-color" content="#0D9488">
  <title>Ponte — Entrar</title>
  <link rel="manifest" href="/manifest.json">
  <link rel="icon" href="/icons/icon-192.png" type="image/png">
  <link rel="stylesheet" href="/styles.css">
</head>
<body class="auth">
  <main class="auth-main">
    <div class="brand">
      <img class="brand-mark" src="/icons/logo-mark.png" alt="">
      <span class="brand-name">ponte</span>
    </div>

    <form id="auth-form" class="auth-form">
      <h1 id="auth-title">Entrar</h1>
      <label>E-mail
        <input type="email" name="email" required autocomplete="username">
      </label>
      <label>Senha
        <input type="password" name="password" required minlength="8" autocomplete="current-password">
      </label>
      <p id="auth-error" class="dialog-error" hidden></p>
      <button type="submit" class="btn-action btn-speak">Entrar</button>
    </form>

    <button type="button" id="auth-toggle" class="btn-ghost auth-toggle">Não tem conta? Criar uma</button>
  </main>

  <script src="/login.js"></script>
</body>
</html>
```

- [ ] **Step 2: Criar `login.js`**

Crie `src/main/resources/static/login.js`:

```js
const API = '/api/v1';

const form = document.getElementById('auth-form');
const title = document.getElementById('auth-title');
const errorEl = document.getElementById('auth-error');
const submitButton = form.querySelector('button[type="submit"]');
const toggleButton = document.getElementById('auth-toggle');

let mode = 'login';

function setMode(next) {
  mode = next;
  if (mode === 'login') {
    title.textContent = 'Entrar';
    submitButton.textContent = 'Entrar';
    toggleButton.textContent = 'Não tem conta? Criar uma';
  } else {
    title.textContent = 'Criar conta';
    submitButton.textContent = 'Criar conta';
    toggleButton.textContent = 'Já tem conta? Entrar';
  }
  errorEl.hidden = true;
}

toggleButton.addEventListener('click', () => setMode(mode === 'login' ? 'register' : 'login'));

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  errorEl.hidden = true;
  submitButton.disabled = true;
  const data = new FormData(form);
  const body = JSON.stringify({ email: data.get('email'), password: data.get('password') });
  const path = mode === 'login' ? '/auth/login' : '/auth/register';
  try {
    const res = await fetch(`${API}${path}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body,
    });
    if (!res.ok) throw new Error();
    location.href = '/';
  } catch {
    errorEl.textContent = mode === 'login'
      ? 'E-mail ou senha inválidos.'
      : 'Não foi possível criar a conta. Verifique os dados e tente de novo.';
    errorEl.hidden = false;
  } finally {
    submitButton.disabled = false;
  }
});
```

- [ ] **Step 3: Adicionar os estilos da tela de login em `styles.css`**

Adicione ao final de `src/main/resources/static/styles.css`:

```css

/* ---------- login/cadastro ---------- */
.auth-main {
  width: min(24rem, 92vw);
  margin: 3rem auto 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1.5rem;
}
.auth-form {
  width: 100%;
  background: var(--surface);
  border-radius: 1.25rem;
  padding: 1.5rem;
  box-shadow: 0 8px 32px rgba(38, 34, 28, 0.15);
}
.auth-form h1 { margin: 0 0 1rem; font-size: 1.3rem; }
.auth-form label {
  display: block;
  margin-bottom: 0.9rem;
  font-weight: 700;
  font-size: 0.95rem;
}
.auth-form input {
  display: block;
  width: 100%;
  margin-top: 0.35rem;
  padding: 0.55rem 0.7rem;
  font-size: 1rem;
  border: 2px solid var(--muted);
  border-radius: 0.6rem;
  background: var(--surface);
}
.auth-form button[type="submit"] {
  width: 100%;
  margin-top: 0.5rem;
  padding: 0.7rem;
}
.auth-toggle { font-size: 0.95rem; }
```

- [ ] **Step 4: Adicionar o botão "sair" a `index.html` e checar 401 em `app.js`**

Em `src/main/resources/static/index.html`, adicione o botão de logout ao
topbar (depois do botão de revogar consentimento):

```html
      <button id="btn-revoke-consent" class="btn-ghost" aria-label="Revogar consentimento" hidden>🔓</button>
      <button id="btn-logout" class="btn-ghost" aria-label="Sair">🚪</button>
```

Em `src/main/resources/static/app.js`, substitua a função `init()`
inteira (mantendo `loadBoard()` como está):

```js
async function init() {
  try {
    const profilesRes = await fetch(`${API}/profiles`);
    if (profilesRes.status === 401) {
      location.href = '/login.html';
      return;
    }
    const profiles = await profilesRes.json();
    if (!profiles.length) throw new Error('nenhum perfil');
    const profile = profiles[0];
    state.childId = profile.id;
    if (profile.hasActiveConsent) {
      document.getElementById('btn-revoke-consent').hidden = false;
      await loadBoard();
    } else {
      // sem consentimento ativo: bloqueia a prancha até o responsável autorizar
      document.getElementById('consent-gate').showModal();
    }
  } catch (err) {
    console.error('Falha ao carregar a prancha:', err);
    document.getElementById('grid').innerHTML =
      '<p class="load-error">Não foi possível carregar a prancha. Verifique a conexão e recarregue a página.</p>';
  }
}
```

E adicione a função de logout e sua chamada de setup logo antes da
chamada final `init();` (que já existe no fim do arquivo):

```js
function setupLogout() {
  document.getElementById('btn-logout').addEventListener('click', async () => {
    try {
      await fetch(`${API}/auth/logout`, { method: 'POST' });
    } finally {
      location.href = '/login.html';
    }
  });
}

setupLogout();

init();
```

(A linha `init();` já existe no arquivo — apenas adicione
`setupLogout();` e a função acima antes dela, sem duplicar `init();`.)

- [ ] **Step 5: Adicionar o botão "sair" a `dashboard.html` e checar 401 em `dashboard.js`**

Em `src/main/resources/static/dashboard.html`, adicione o botão de
logout ao topbar (depois do link "Voltar à prancha"):

```html
      <a class="btn-ghost" href="/" aria-label="Voltar à prancha">← Prancha</a>
      <button id="btn-logout" class="btn-ghost" aria-label="Sair">🚪</button>
```

Em `src/main/resources/static/dashboard.js`, substitua a função `init()`
inteira:

```js
async function init() {
  try {
    const profilesRes = await fetch(`${API}/profiles`);
    if (profilesRes.status === 401) {
      location.href = '/login.html';
      return;
    }
    if (!profilesRes.ok) throw new Error(`profiles ${profilesRes.status}`);
    const profiles = await profilesRes.json();
    if (!profiles.length) throw new Error('nenhum perfil');
    const child = profiles[0];
    document.getElementById('child-name').textContent = child.displayName;

    const summaryRes = await fetch(`${API}/usage/summary?childId=${child.id}`);
    if (!summaryRes.ok) throw new Error(`summary ${summaryRes.status}`);
    const summary = await summaryRes.json();

    document.getElementById('tile-taps').textContent = summary.totalTaps;
    document.getElementById('tile-sentences').textContent = summary.sentencesSpoken;
    document.getElementById('tile-predictions').textContent = summary.predictionsAccepted;

    renderBars(summary.topSymbols);
    renderLegend(summary.topSymbols);
  } catch (err) {
    console.error('Falha ao carregar o painel:', err);
    const bars = document.getElementById('bars');
    bars.innerHTML = '';
    const error = document.createElement('p');
    error.className = 'empty';
    error.textContent = 'Não foi possível carregar os dados. Verifique a conexão e recarregue a página.';
    bars.appendChild(error);
  }
}
```

E substitua a última linha do arquivo (`init();`) por:

```js
document.getElementById('btn-logout').addEventListener('click', async () => {
  try {
    await fetch(`${API}/auth/logout`, { method: 'POST' });
  } finally {
    location.href = '/login.html';
  }
});

init();
```

- [ ] **Step 6: Atualizar `sw.js`**

Em `src/main/resources/static/sw.js`, adicione `login.html` e `login.js`
ao array `SHELL` (junto aos outros arquivos `.html`/`.js`) e faça o bump
de versão de `CACHE`:

```js
const CACHE = 'ponte-v5'; // bump a cada mudança de asset estático
const SHELL = [
  '/',
  '/index.html',
  '/styles.css',
  '/app.js',
  '/dashboard.html',
  '/dashboard.js',
  '/login.html',
  '/login.js',
  '/manifest.json',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/logo-mark.png',
];
```

- [ ] **Step 7: Rodar a suíte de backend inteira (garantia de que nada quebrou)**

Run: `mvn -q test`
Expected: PASS — 34 testes, mesma contagem da Task 6 (esta task não
altera nenhum arquivo Java).

- [ ] **Step 8: Verificar manualmente no navegador**

Suba o servidor (`mvn spring-boot:run`) e confirme, em sequência:

1. Acessar `http://localhost:8080/` sem sessão → redireciona para
   `/login.html`.
2. Fazer login com `demo@ponte.app` / `ponte1234` → volta para `/` e
   mostra a prancha da "Alex" (com o gate de consentimento, se aplicável
   — a conta demo já tem consentimento ativo, então a prancha deve
   aparecer direto).
3. Clicar "🚪 Sair" → volta para `/login.html`; acessar `/` de novo sem
   logar novamente → redireciona para login (a sessão foi mesmo encerrada).
4. Clicar "Não tem conta? Criar uma", cadastrar uma conta nova
   (`teste@ponte.app` / senha com 8+ caracteres) → deve entrar
   automaticamente e mostrar o gate de consentimento (essa conta nova
   ainda não tem nenhum perfil de criança — **nota:** como esta fatia não
   inclui criação de perfil de criança pela conta, uma conta nova sem
   perfil vai cair no `catch` de "Falha ao carregar a prancha" por
   `profiles.length === 0`; isso é esperado e aceitável nesta fatia, veja
   "Fora de escopo" no spec).
5. Acessar `http://localhost:8080/dashboard.html` sem sessão →
   redireciona para `/login.html`.

- [ ] **Step 9: Commit**

```bash
git add src/main/resources/static/login.html src/main/resources/static/login.js src/main/resources/static/styles.css src/main/resources/static/index.html src/main/resources/static/app.js src/main/resources/static/dashboard.html src/main/resources/static/dashboard.js src/main/resources/static/sw.js
git commit -m "feat: telas de login/cadastro, redirect em 401 e botão de logout"
```

---

## Task 8: Atualizar o README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Atualizar "Como rodar" com as credenciais demo**

Em `README.md`, troque a linha (linha 63 antes desta task):

```markdown
O banco é H2 em memória com seed automático (perfil demo + símbolos padrão por emoji) — não precisa configurar nada para experimentar.
```

por:

```markdown
O banco é H2 em memória com seed automático (uma conta demo dona de um perfil com símbolos padrão por emoji) — não precisa configurar nada para experimentar. Login: `demo@ponte.app` / `ponte1234` (ou clique em "Criar uma" na tela de entrada para cadastrar sua própria conta).
```

- [ ] **Step 2: Adicionar os endpoints de autenticação à tabela da API**

Na tabela em `## 🔌 API REST (`/api/v1`)`, adicione estas 4 linhas antes
da linha `| \`GET /profiles\` | ... |`:

```markdown
| `POST /auth/register` | Cria conta (e-mail + senha) e já autentica |
| `POST /auth/login` | Autentica por sessão (cookie) |
| `POST /auth/logout` | Encerra a sessão |
| `GET /auth/me` | Conta autenticada atual |
```

- [ ] **Step 3: Marcar o item do roadmap como concluído**

Troque:

```markdown
- [ ] Autenticação e ownership (dashboard e predição)
```

por:

```markdown
- [x] Autenticação e ownership: login por conta + posse do perfil já existente (múltiplos perfis por conta fica para depois)
```

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: credenciais demo, endpoints de auth e roadmap atualizado"
```

---

## Verificação final

- [ ] Rodar `mvn -q test` — todos os testes (34) devem passar.
- [ ] Repetir o roteiro manual do Step 8 da Task 7 no navegador.
- [ ] Conferir que `git log --oneline` mostra uma sequência de commits pequenos e coerente com as 8 tasks acima.

