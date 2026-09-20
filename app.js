const KEY = "todo-app-tasks";
const form = document.getElementById("add-form");
const input = document.getElementById("new-task");
const list = document.getElementById("task-list");
const emptyMessage = document.getElementById("empty");
const summary = document.getElementById("summary");
const clearButton = document.getElementById("clear-done");
const filterButtons = document.querySelectorAll(".filter");

let todos = load();
let nextId = todos.reduce((m, t) => Math.max(m, t.id), 0) + 1;
let filter = "all";

function load() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY));
    return Array.isArray(saved) ? saved : [];
  } catch (e) {
    return [];
  }
}

function save() {
  try {
    localStorage.setItem(KEY, JSON.stringify(todos));
  } catch (e) {}
  render();
}

function render() {
  const remaining = todos.filter((t) => !t.done).length;
  if (todos.length === 0) summary.textContent = "Nothing on your list yet.";
  else if (remaining === 0) summary.textContent = "All " + todos.length + " tasks done.";
  else summary.textContent = remaining + (remaining === 1 ? " task" : " tasks") + " left to do.";

  const visible = todos.filter((t) =>
    filter === "active" ? !t.done : filter === "done" ? t.done : true
  );
  list.innerHTML = "";
  visible.forEach((t) => list.appendChild(makeItem(t)));

  const msgs = { all: "Add your first task above.", active: "Nothing left to do.", done: "No completed tasks yet." };
  emptyMessage.hidden = visible.length > 0;
  emptyMessage.textContent = msgs[filter];
  clearButton.hidden = todos.length === remaining;
}

function makeItem(todo) {
  const li = document.createElement("li");
  li.className = "task" + (todo.done ? " done" : "");

  const box = document.createElement("input");
  box.type = "checkbox";
  box.checked = todo.done;
  box.id = "task-" + todo.id;
  box.addEventListener("change", () => {
    todos = todos.map((t) => (t.id === todo.id ? { ...t, done: !t.done } : t));
    save();
  });

  const label = document.createElement("label");
  label.className = "task-text";
  label.htmlFor = box.id;
  label.textContent = todo.text;

  const del = document.createElement("button");
  del.type = "button";
  del.className = "delete";
  del.textContent = "\u00d7";
  del.setAttribute("aria-label", "Delete task: " + todo.text);
  del.addEventListener("click", () => {
    todos = todos.filter((t) => t.id !== todo.id);
    save();
  });

  li.append(box, label, del);
  return li;
}

form.addEventListener("submit", (e) => {
  e.preventDefault();
  const text = input.value.trim();
  if (!text) return;
  todos.push({ id: nextId++, done: false, text: text });
  input.value = "";
  input.focus();
  save();
});

filterButtons.forEach((b) =>
  b.addEventListener("click", () => {
    filter = b.dataset.filter;
    filterButtons.forEach((x) => x.classList.toggle("active", x === b));
    render();
  })
);

clearButton.addEventListener("click", () => {
  todos = todos.filter((t) => !t.done);
  save();
});

render();
