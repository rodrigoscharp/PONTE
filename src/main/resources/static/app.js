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
}

// Registro de eventos de uso com fila offline: o evento entra na fila
// local primeiro e é enviado quando houver rede (sala de aula sem
// internet garantida). occurredAt é gravado no momento do toque.
const QUEUE_KEY = 'ponte.eventQueue';
let flushing = false;

function recordEvent(eventType, symbolId) {
  const queue = JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
  queue.push({
    childId: state.childId,
    symbolId,
    eventType,
    occurredAt: new Date().toISOString(),
  });
  localStorage.setItem(QUEUE_KEY, JSON.stringify(queue));
  flushQueue();
}

async function flushQueue() {
  if (flushing || !navigator.onLine) return;
  const queue = JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
  if (queue.length === 0) return;

  flushing = true;
  localStorage.setItem(QUEUE_KEY, '[]');
  const failed = [];
  for (const event of queue) {
    try {
      const res = await fetch(`${API}/usage-events`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(event),
      });
      if (!res.ok) failed.push(event);
    } catch {
      failed.push(event);
    }
  }
  if (failed.length > 0) {
    const current = JSON.parse(localStorage.getItem(QUEUE_KEY) || '[]');
    localStorage.setItem(QUEUE_KEY, JSON.stringify(failed.concat(current)));
  }
  flushing = false;
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
});

document.getElementById('btn-clear').addEventListener('click', clearSentence);

init();
