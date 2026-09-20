const form = document.getElementById("add-form");
const input = document.getElementById("new-task");
const list = document.getElementById("task-list");
const emptyMessage = document.getElementById("empty");
const summary = document.getElementById("summary");
const clearButton = document.getElementById("clear-done");
const filterButtons = document.querySelectorAll(".filter");

let todos = [];
let currentFilter = "all";

// ---------- Talk to the Java server ----------
async function request(url, options) {
  try {
    const response = await fetch(url, options);
    if (!response.ok) throw new Error("Server returned " + response.status);
    todos = await response.json();
    render();
  } catch (error) {
    summary.textContent = "Can't reach the server. Check that TodoApp is still running.";
  }
}

const loadTodos = () => request("/api/todos");
const addTodo = (text) => request("/api/todos", { method: "POST", body: text });
const toggleTodo = (id) => request("/api/toggle?id=" + id, { method: "POST" });
const deleteTodo = (id) => request("/api/delete?id=" + id, { method: "POST" });
const clearDone = () => request("/api/clear-done", { method: "POST" });

// ---------- Draw the page ----------
function render() {
  const remaining = todos.filter((t) => !t.done).length;
  const doneCount = todos.length - remaining;

  if (todos.length === 0) {
    summary.textContent = "Nothing on your list yet.";
  } else if (remaining === 0) {
    summary.textContent = "All " + todos.length + " tasks done.";
  } else {
    summary.textContent = remaining + (remaining === 1 ? " task" : " tasks") + " left to do.";
  }

  const visible = todos.filter((t) => {
    if (currentFilter === "active") return !t.done;
    if (currentFilter === "done") return t.done;
    return true;
  });

  list.innerHTML = "";
  visible.forEach((todo) => list.appendChild(createTaskElement(todo)));

  const messages = {
    all: "Add your first task above.",
    active: "Nothing left to do.",
    done: "No completed tasks yet.",
  };
  emptyMessage.hidden = visible.length > 0;
  emptyMessage.textContent = messages[currentFilter];

  clearButton.hidden = doneCount === 0;
}

function createTaskElement(todo) {
  const li = document.createElement("li");
  li.className = "task" + (todo.done ? " done" : "");

  const checkbox = document.createElement("input");
  checkbox.type = "checkbox";
  checkbox.checked = todo.done;
  checkbox.id = "task-" + todo.id;
  checkbox.addEventListener("change", () => toggleTodo(todo.id));

  // textContent (not innerHTML) keeps user text safe from HTML injection
  const label = document.createElement("label");
  label.className = "task-text";
  label.htmlFor = checkbox.id;
  label.textContent = todo.text;

  const remove = document.createElement("button");
  remove.type = "button";
  remove.className = "delete";
  remove.textContent = "\u00d7";
  remove.setAttribute("aria-label", "Delete task: " + todo.text);
  remove.addEventListener("click", () => deleteTodo(todo.id));

  li.append(checkbox, label, remove);
  return li;
}

// ---------- Events ----------
form.addEventListener("submit", (event) => {
  event.preventDefault();
  const text = input.value.trim();
  if (!text) return;
  addTodo(text);
  input.value = "";
  input.focus();
});

filterButtons.forEach((button) => {
  button.addEventListener("click", () => {
    currentFilter = button.dataset.filter;
    filterButtons.forEach((b) => b.classList.toggle("active", b === button));
    render();
  });
});

clearButton.addEventListener("click", clearDone);

loadTodos();