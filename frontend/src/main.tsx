import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.js'
import {DesktopStartup} from './desktop/DesktopStartup.js'
import {MotionProvider} from './ui/MotionProvider.js'
import {UpdateProvider} from './desktop/updates/UpdateProvider.js'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MotionProvider><UpdateProvider><DesktopStartup><App /></DesktopStartup></UpdateProvider></MotionProvider>
  </StrictMode>,
)
