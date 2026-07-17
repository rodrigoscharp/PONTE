const API = '/api/v1';

const state = {
  childId: null,
  symbols: [],
  sentence: [],
};

const CELL_CLASS = {
  COMIDA: 'cell--comida',
  SENTIMENTOS: 'cell--sentimentos',
  PESSOAS: 'cell--pessoas',
  ACOES: 'cell--acoes',
  PERSONALIZADO: 'cell--personalizado',
};

async function init() {
  try {
    const profiles = await fetch(`${API}/profiles`).then((r) => r.json());
    if (!profiles.length) throw new Error('nenhum perfil');
    state.childId = profiles[0].id;
    state.symbols = await fetch(`${API}/symbols?childId=${state.childId}`).then((r) => r.json());
    renderGrid();
    renderSentence();
    flushQueue(); // reenviar eventos que ficaram na fila de uma sessão offline
  } catch {
    document.getElementById('grid').innerHTML =
      '<p class="load-error">Não foi possível carregar a prancha. Verifique a conexão e recarregue a página.</p>';
  }
}

function renderGrid() {
  const grid = document.getElementById('grid');
  grid.innerHTML = '';
  // ordem estável por gridPosition: a prancha nunca é reorganizada (motor planning)
  [...state.symbols]
    .sort((a, b) => a.gridPosition - b.gridPosition)
    .forEach((symbol) => grid.appendChild(createCell(symbol)));
}

function createCell(symbol) {
  const cell = document.createElement('button');
  cell.className = `cell ${CELL_CLASS[symbol.category] || 'cell--personalizado'}`;
  cell.type = 'button';

  const figure = document.createElement('span');
  figure.className = 'cell-figure';
  if (symbol.imageType === 'EMOJI') {
    figure.textContent = symbol.imageRef;
  } else {
    const img = document.createElement('img');
    img.src = symbol.imageRef;
    img.alt = '';
    figure.appendChild(img);
  }

  const label = document.createElement('span');
  label.className = 'cell-label';
  label.textContent = symbol.label;

  cell.append(figure, label);
  cell.addEventListener('click', () => onSymbolTap(symbol));
  return cell;
}

function onSymbolTap(symbol) {
  speak(symbol.label);
  state.sentence.push(symbol);
  renderSentence();
  recordEvent('SYMBOL_TAP', symbol.id);
  maybeSuggest();
}

function speak(text) {
  if (!('speechSynthesis' in window)) return;
  speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(text);
  utterance.lang = 'pt-BR';
  utterance.rate = 0.9;
  speechSynthesis.speak(utterance);
}

function renderSentence() {
  const bar = document.getElementById('sentence-bar');
  bar.innerHTML = '';
  if (state.sentence.length === 0) {
    const hint = document.createElement('span');
    hint.className = 'sentence-hint';
    hint.textContent = 'Toque nos símbolos para montar a frase';
    bar.appendChild(hint);
    return;
  }
  state.sentence.forEach((symbol) => {
    const chip = document.createElement('span');
    chip.className = 'chip';
    chip.textContent = symbol.imageType === 'EMOJI'
      ? `${symbol.imageRef} ${symbol.label}`
      : symbol.label;
    bar.appendChild(chip);
  });
}

function sentenceText() {
  return state.sentence.map((s) => s.label).join(' ');
}

function clearSentence() {
  state.sentence = [];
  renderSentence();
  maybeSuggest();
}

// Registro de eventos de uso com fila offline: o evento entra na fila
// local primeiro e é enviado quando houver rede (sala de aula sem
// internet garantida). occurredAt é gravado no momento do toque.
// Um evento só sai da fila DEPOIS do envio confirmado — fechar a aba
// no meio do envio não perde nada (no pior caso, reenvia).
const QUEUE_KEY = 'ponte.eventQueue';
let flushing = false;

function readQueue() {
  try {
    const parsed = JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return []; // storage corrompido: recomeça a fila em vez de travar os toques
  }
}

function writeQueue(queue) {
  try {
    localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  } catch {
    // sem espaço no storage: o evento é descartado, mas o toque não falha
  }
}

function recordEvent(eventType, symbolId) {
  const queue = readQueue();
  queue.push({
    childId: state.childId,
    symbolId,
    eventType,
    occurredAt: new Date().toISOString(),
  });
  writeQueue(queue);
  flushQueue();
}

async function flushQueue() {
  if (flushing || !navigator.onLine) return;
  flushing = true;
  try {
    let queue = readQueue();
    while (queue.length > 0) {
      try {
        const res = await fetch(`${API}/usage-events`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(queue[0]),
        });
        // 4xx = rejeição definitiva (ex.: consentimento revogado): descarta o
        // evento para não travar a fila; 5xx = problema transitório: tenta depois.
        if (res.status >= 500) break;
      } catch {
        break; // sem rede no meio do flush: o restante fica na fila
      }
      queue = readQueue();
      queue.shift(); // novos eventos entram só no fim, então [0] é o que foi enviado ou descartado
      writeQueue(queue);
    }
  } finally {
    flushing = false;
  }
}

window.addEventListener('online', flushQueue);

document.getElementById('btn-speak').addEventListener('click', () => {
  if (state.sentence.length === 0) return;
  speak(sentenceText());
  recordEvent('SENTENCE_SPOKEN', null);
});

document.getElementById('btn-undo').addEventListener('click', () => {
  state.sentence.pop();
  renderSentence();
  maybeSuggest();
});

document.getElementById('btn-clear').addEventListener('click', clearSentence);

// Sequência das requisições de predição: só a resposta da requisição mais
// recente pode mexer na UI — resposta antiga chegando atrasada é descartada
// (senão uma sugestão obsoleta reapareceria depois de a frase mudar).
let suggestRequestId = 0;

function hideSuggestion() {
  const el = document.getElementById('suggestion');
  el.hidden = true;
  el.innerHTML = '';
}

async function maybeSuggest() {
  const requestId = ++suggestRequestId;
  if (state.sentence.length < 2) {
    hideSuggestion();
    return;
  }
  let suggestion = null;
  try {
    const res = await fetch(`${API}/predictions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        childId: state.childId,
        symbolIds: state.sentence.map((s) => s.id),
      }),
    });
    if (res.ok) {
      const { suggestions } = await res.json();
      if (suggestions.length > 0) suggestion = suggestions[0];
    }
  } catch {
    // offline: sem sugestão — a prancha continua funcionando normalmente
  }
  if (requestId !== suggestRequestId) return; // resposta antiga: o estado já mudou
  if (suggestion) {
    showSuggestion(suggestion);
  } else {
    hideSuggestion(); // predição vazia ou falha: não deixar chip obsoleto clicável
  }
}

function showSuggestion(text) {
  const el = document.getElementById('suggestion');
  el.innerHTML = '';
  const chip = document.createElement('button');
  chip.type = 'button';
  chip.className = 'suggestion-chip';
  chip.textContent = `💡 ${text}`;
  chip.addEventListener('click', () => {
    speak(text);
    recordEvent('PREDICTION_ACCEPTED', null);
    clearSentence();
  });
  el.appendChild(chip);
  el.hidden = false;
}

function appendCell(symbol) {
  // append-only: o símbolo novo entra no fim; os existentes não são tocados
  state.symbols.push(symbol);
  document.getElementById('grid').appendChild(createCell(symbol));
}

function setupAddSymbol() {
  const dialog = document.getElementById('add-dialog');
  const form = document.getElementById('add-form');
  const cancelButton = document.getElementById('add-cancel');
  const submitButton = form.querySelector('button[type="submit"]');

  document.getElementById('btn-add').addEventListener('click', () => dialog.showModal());
  cancelButton.addEventListener('click', () => {
    form.reset();
    dialog.close();
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    // trava os botões durante o envio: evita duplo toque (símbolo duplicado)
    // e cancelamento no meio do envio (símbolo apareceria após fechar)
    submitButton.disabled = true;
    cancelButton.disabled = true;
    const data = new FormData(form);
    data.append('childId', state.childId);
    try {
      const res = await fetch(`${API}/symbols/custom`, { method: 'POST', body: data });
      if (!res.ok) throw new Error();
      appendCell(await res.json());
      form.reset();
      dialog.close();
    } catch {
      alert('Não foi possível adicionar o símbolo. Verifique a conexão e tente de novo.');
    } finally {
      submitButton.disabled = false;
      cancelButton.disabled = false;
    }
  });
}

setupAddSymbol();

init();
