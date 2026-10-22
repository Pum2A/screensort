import {createContext} from 'react'
import type {DesktopStatus} from './status.js'

export const DesktopRuntime = createContext<{status: DesktopStatus; start: () => void} | null>(null)
