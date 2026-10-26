import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
// Bundled, not loaded from the web: the desktop app works offline.
import '@fontsource-variable/inter/wght.css'
import '@fontsource/jetbrains-mono/400.css'
import '@fontsource/jetbrains-mono/500.css'
import '@fontsource/geist/600.css'
import './index.css'
import App from './App.js'
import {DesktopStartup} from './desktop/DesktopStartup.js'
import {MotionProvider} from './ui/MotionProvider.js'
import {UpdateProvider} from './desktop/updates/UpdateProvider.js'
import {QueryProvider} from './shared/QueryProvider.js'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryProvider>
      <MotionProvider><UpdateProvider><DesktopStartup><App /></DesktopStartup></UpdateProvider></MotionProvider>
    </QueryProvider>
  </StrictMode>,
)
