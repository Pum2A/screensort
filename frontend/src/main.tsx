import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {DesktopStartup} from './desktop/DesktopStartup'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <DesktopStartup><App /></DesktopStartup>
  </StrictMode>,
)
