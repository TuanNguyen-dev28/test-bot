const form = document.querySelector('#chat-form');
const input = document.querySelector('#question');
const messages = document.querySelector('#messages');
const status = document.querySelector('#connection-status');
const clearButton = document.querySelector('#clear-chat');
const list = document.querySelector('#fabric-list');
const initialMessages = messages.cloneNode(true);
let sending = false;

function addMessage(text, role = 'assistant') {
  const item = document.createElement('div');
  item.className = `message ${role}`;
  const label = document.createElement('span');
  label.className = 'message-label';
  label.textContent = role === 'user' ? 'BẠN' : 'TRỢ LÝ XƯỞNG VẢI';
  const paragraph = document.createElement('p');
  paragraph.textContent = text;
  item.append(label, paragraph);
  messages.append(item);
  messages.scrollTop = messages.scrollHeight;
  return item;
}

function setBusy(busy) {
  sending = busy;
  document.querySelectorAll('button').forEach(button => { button.disabled = busy; });
  input.disabled = busy;
  form.setAttribute('aria-busy', String(busy));
}

async function request(url, options = {}) {
  const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) });
  const data = await response.json();
  if (!response.ok) throw new Error(data.error || 'Không thể xử lý yêu cầu. Bạn thử lại nhé.');
  return data;
}

async function sendQuestion(question) {
  question = question.trim();
  if (!question || sending) return;
  if (question.length > 1000) return;
  addMessage(question, 'user');
  input.value = '';
  setBusy(true);
  const pending = addMessage('Đang tra cứu…');
  try {
    const data = await request('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: question })
    });
    pending.remove();
    addMessage(data.answer);
    status.textContent = 'Sẵn sàng hỗ trợ';
  } catch (error) {
    pending.remove();
    const connectionError = error.name === 'TypeError' || error.name === 'TimeoutError' || error.name === 'SyntaxError';
    addMessage(connectionError ? 'Chưa kết nối được với máy chủ. Bạn hãy kiểm tra kết nối rồi gửi lại câu hỏi.' : error.message, 'error');
    status.textContent = connectionError ? 'Kết nối bị gián đoạn' : 'Vui lòng thử lại';
    input.value = question;
  } finally {
    setBusy(false);
    input.focus();
  }
}

form.addEventListener('submit', event => {
  event.preventDefault();
  sendQuestion(input.value);
});
document.querySelectorAll('[data-question]').forEach(button => {
  button.addEventListener('click', () => sendQuestion(button.dataset.question));
});
clearButton.addEventListener('click', () => {
  messages.replaceChildren(...Array.from(initialMessages.children, item => item.cloneNode(true)));
  input.value = '';
  input.focus();
});

async function loadFabrics() {
  try {
    const fabrics = await request('/api/fabrics');
    list.replaceChildren();
    const colors = { 'đen': '#33372f', 'trắng': '#fffef9', 'be': '#d7c5a8', 'nâu': '#8c6951' };
    for (const [key, fabric] of Object.entries(fabrics)) {
      const card = document.createElement('button');
      card.type = 'button';
      card.className = 'fabric-card';
      card.disabled = sending;
      card.setAttribute('aria-label', `Hỏi giá ${fabric.name}`);
      const swatch = document.createElement('div');
      swatch.className = key === 'linen' ? 'swatch linen' : 'swatch';
      swatch.setAttribute('aria-hidden', 'true');
      const swatchLabel = document.createElement('span');
      swatchLabel.className = 'swatch-label';
      swatchLabel.textContent = key.toUpperCase();
      swatch.append(swatchLabel);
      const body = document.createElement('div');
      body.className = 'card-body';
      const heading = document.createElement('div');
      heading.className = 'card-heading';
      const name = document.createElement('strong');
      name.textContent = fabric.name;
      const arrow = document.createElement('span');
      arrow.textContent = '↗';
      heading.append(name, arrow);
      const price = document.createElement('p');
      price.className = 'card-price';
      price.textContent = new Intl.NumberFormat('vi-VN').format(fabric.price) + ' đồng';
      const palette = document.createElement('div');
      palette.className = 'card-colors';
      for (const color of fabric.colors) {
        const dot = document.createElement('span');
        dot.className = 'color-dot';
        dot.style.backgroundColor = colors[color] || '#b8beae';
        dot.setAttribute('aria-hidden', 'true');
        palette.append(dot);
      }
      const colorLabel = document.createElement('span');
      colorLabel.className = 'color-label';
      colorLabel.textContent = fabric.colors.join(', ');
      palette.append(colorLabel);
      body.append(heading, price, palette);
      card.append(swatch, body);
      card.addEventListener('click', () => sendQuestion(`Giá vải ${key} bao nhiêu?`));
      list.append(card);
    }
    document.querySelector('#fabric-count').textContent = String(Object.keys(fabrics).length).padStart(2, '0');
    status.textContent = 'Sẵn sàng hỗ trợ';
  } catch {
    list.textContent = 'Chưa tải được danh sách vải. ';
    const retry = document.createElement('button');
    retry.type = 'button';
    retry.textContent = 'Thử lại';
    retry.addEventListener('click', loadFabrics);
    list.append(retry);
    status.textContent = 'Chưa kết nối được máy chủ';
  }
}

loadFabrics();
