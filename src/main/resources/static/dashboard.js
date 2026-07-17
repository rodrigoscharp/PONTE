const API = '/api/v1';

const CATEGORY_COLORS = {
  COMIDA: '#EA580C',
  SENTIMENTOS: '#3B82F6',
  PESSOAS: '#CA8A04',
  ACOES: '#0D9488',
  PERSONALIZADO: '#A21CAF',
};

const CATEGORY_NAMES = {
  COMIDA: 'Comida',
  SENTIMENTOS: 'Sentimentos',
  PESSOAS: 'Pessoas',
  ACOES: 'Ações',
  PERSONALIZADO: 'Personalizado',
};

async function init() {
  try {
    const profiles = await fetch(`${API}/profiles`).then((r) => r.json());
    if (!profiles.length) throw new Error('nenhum perfil');
    const child = profiles[0];
    document.getElementById('child-name').textContent = child.displayName;

    const summary = await fetch(`${API}/usage/summary?childId=${child.id}`).then((r) => r.json());

    document.getElementById('tile-taps').textContent = summary.totalTaps;
    document.getElementById('tile-sentences').textContent = summary.sentencesSpoken;
    document.getElementById('tile-predictions').textContent = summary.predictionsAccepted;

    renderBars(summary.topSymbols);
    renderLegend(summary.topSymbols);
  } catch {
    const bars = document.getElementById('bars');
    bars.innerHTML = '';
    const error = document.createElement('p');
    error.className = 'empty';
    error.textContent = 'Não foi possível carregar os dados. Verifique a conexão e recarregue a página.';
    bars.appendChild(error);
  }
}

function renderBars(topSymbols) {
  const container = document.getElementById('bars');
  container.innerHTML = '';

  if (topSymbols.length === 0) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = 'Nenhum uso registrado hoje ainda. Os toques na prancha aparecem aqui.';
    container.appendChild(empty);
    return;
  }

  const max = Math.max(...topSymbols.map((s) => s.count));
  topSymbols.forEach((s) => {
    const row = document.createElement('div');
    row.className = 'bar-row';
    row.title = `${s.label} — ${s.count} ${s.count === 1 ? 'toque' : 'toques'}`;

    const label = document.createElement('span');
    label.className = 'bar-label';
    label.textContent = s.label;

    const track = document.createElement('div');
    track.className = 'bar-track';
    const fill = document.createElement('div');
    fill.className = 'bar-fill';
    fill.style.width = `${(s.count / max) * 100}%`;
    fill.style.background = CATEGORY_COLORS[s.category] || CATEGORY_COLORS.PERSONALIZADO;
    track.appendChild(fill);

    const count = document.createElement('span');
    count.className = 'bar-count';
    count.textContent = s.count;

    row.append(label, track, count);
    container.appendChild(row);
  });
}

function renderLegend(topSymbols) {
  const container = document.getElementById('legend');
  container.innerHTML = '';
  const present = [...new Set(topSymbols.map((s) => s.category))];
  present.forEach((category) => {
    const item = document.createElement('span');
    item.className = 'legend-item';
    const dot = document.createElement('span');
    dot.className = 'legend-dot';
    dot.style.background = CATEGORY_COLORS[category] || CATEGORY_COLORS.PERSONALIZADO;
    item.append(dot, document.createTextNode(CATEGORY_NAMES[category] || category));
    container.appendChild(item);
  });
}

init();
