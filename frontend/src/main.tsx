import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import {DesktopStartup} from './desktop/DesktopStartup'
import {MotionProvider} from './ui/MotionProvider'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MotionProvider><DesktopStartup><App /></DesktopStartup></MotionProvider>
  </StrictMode>,
)
