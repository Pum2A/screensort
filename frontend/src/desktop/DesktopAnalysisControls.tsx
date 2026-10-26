import {useContext, useRef, useState} from 'react'
import {AnimatePresence} from 'motion/react'
import {CircleAlert} from 'lucide-react'
import {DesktopRuntime} from './runtimeContext.js'
import {AiPackagePanel} from './packages/AiPackagePanel.js'
import {Modal} from '../ui/Modal.js'
import {PixelIcon} from '../ui/PixelIcon.js'

export function DesktopAnalysisControls() {
    const runtime = useContext(DesktopRuntime)
    const [open, setOpen] = useState(false)
    const trigger = useRef<HTMLButtonElement>(null)
    if (!runtime) return <div className="engine-card browser-mode"><span className="engine-chip"><PixelIcon name="cpu"/></span><div><strong>Tryb przeglądarkowy</strong><small>usługi osobno</small></div></div>
    const {status} = runtime
    const label = status.analysisReady ? 'gotowy · lokalnie' : status.analysisStarting ? 'uruchamiamy…' : status.error ? 'wymaga uwagi' : 'na żądanie'
    return <>
        <button ref={trigger} className="engine-card" data-state={status.error ? 'error' : status.analysisReady ? 'ready' : 'idle'} onClick={() => setOpen(true)} aria-haspopup="dialog">
            <span className="engine-chip"><PixelIcon name="cpu"/></span>
            <span><strong>Silnik AI</strong><small>{label}</small></span>
            {status.analysisStarting && <span className="engine-progress" aria-hidden="true"><i/></span>}
        </button>
        <AnimatePresence>{open && <Modal key="engine" title="Analiza na Twoim komputerze" subtitle="SILNIK ANALIZY" trigger={trigger} onClose={() => setOpen(false)}>
            {status.error && <div className="feedback feedback-error" role="alert"><CircleAlert size={19}/><div><strong>Silnik nie jest gotowy</strong><p>{status.error}</p></div></div>}
            <AiPackagePanel starting={status.analysisStarting} ready={status.analysisReady} onStart={runtime.start}/>
        </Modal>}</AnimatePresence>
    </>
}
