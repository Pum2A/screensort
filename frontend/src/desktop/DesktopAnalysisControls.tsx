import {useContext, useRef, useState} from 'react'
import {AnimatePresence} from 'motion/react'
import {ChevronRight, CircleAlert, Cpu, LoaderCircle} from 'lucide-react'
import {DesktopRuntime} from './runtimeContext'
import {AiPackagePanel} from './packages/AiPackagePanel'
import {Modal} from '../ui/Modal'

export function DesktopAnalysisControls() {
    const runtime = useContext(DesktopRuntime)
    const [open, setOpen] = useState(false)
    const trigger = useRef<HTMLButtonElement>(null)
    if (!runtime) return <div className="engine-card browser-mode"><Cpu size={18}/><div><strong>Tryb przeglądarkowy</strong><small>Usługi uruchamiane osobno</small></div></div>
    const {status} = runtime
    const label = status.analysisReady ? 'Gotowy do pracy' : status.analysisStarting ? 'Przygotowujemy silnik' : status.error ? 'Wymaga uwagi' : 'Uruchamiany na żądanie'
    return <>
        <button ref={trigger} className="engine-card" data-state={status.error ? 'error' : status.analysisReady ? 'ready' : 'idle'} onClick={() => setOpen(true)} aria-haspopup="dialog">
            {status.analysisStarting ? <LoaderCircle size={19} className="spin"/> : status.error ? <CircleAlert size={19}/> : <Cpu size={19}/>}
            <span><strong>Silnik analizy</strong><small>{label}</small></span><ChevronRight size={15}/>
        </button>
        <AnimatePresence>{open && <Modal key="engine" title="Analiza na Twoim komputerze" subtitle="SILNIK ANALIZY" trigger={trigger} onClose={() => setOpen(false)}>
            {status.error && <div className="feedback feedback-error" role="alert"><CircleAlert size={19}/><div><strong>Silnik nie jest gotowy</strong><p>{status.error}</p></div></div>}
            <AiPackagePanel starting={status.analysisStarting} ready={status.analysisReady} onStart={runtime.start}/>
        </Modal>}</AnimatePresence>
    </>
}
