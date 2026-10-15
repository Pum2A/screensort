import {createContext, useContext} from 'react'

export const AnalysisReadiness = createContext(true)
export function useAnalysisReady() { return useContext(AnalysisReadiness) }
