# ClassroomLAN Frontend

Vue 3 + Vite application for ClassroomLAN.

## Structure

- `src/main.js` — entry
- `src/App.vue` — root layout (sidebar + header)
- `src/router/index.js` — SPA routes (hash mode)
- `src/stores/` — Pinia state (node, game, chat)
- `src/composables/useWebSocket.js` — reusable WS client
- `src/views/` — page components
  - `Home.vue`
  - `FileTransfer.vue`
  - `GameLobby.vue`
  - `games/` — DrawGuess.vue, Quiz.vue, Werewolf.vue

## Build

```bash
npm install
npm run dev     # development
npm run build   # production to backend/src/main/resources/static
```

## Build output

The Vite build outputs directly to `backend/src/main/resources/static/` which is served by `StaticHandler.java`.
