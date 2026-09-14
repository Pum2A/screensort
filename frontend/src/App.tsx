import './App.css'

function App() {
  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="/" aria-label="ScreenSort home">
          <span className="brand-mark" aria-hidden="true">S</span>
          <span>ScreenSort</span>
        </a>
        <span className="local-badge">Local only</span>
      </header>

      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Private image workspace</p>
        <h1 id="page-title">Turn a folder of screenshots into a useful catalog.</h1>
        <p className="description">
          ScreenSort will scan only the folder you choose. Source images remain
          read-only while the app prepares safe metadata for review.
        </p>

        <div className="action-row">
          <button type="button" disabled>
            Choose folder
          </button>
          <p>Native folder selection is the next integration step.</p>
        </div>
      </section>

      <section className="status-grid" aria-label="ScreenSort architecture status">
        <article>
          <span>Frontend</span>
          <strong>React + TypeScript</strong>
          <p>Component-based interface prepared.</p>
        </article>
        <article>
          <span>Backend</span>
          <strong>Spring Boot API</strong>
          <p>The existing safe scanner stays in Java.</p>
        </article>
        <article>
          <span>Filesystem</span>
          <strong>Read-only source</strong>
          <p>No image will be moved or modified.</p>
        </article>
      </section>
    </main>
  )
}

export default App
