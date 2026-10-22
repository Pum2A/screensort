import {useEffect, useState} from 'react'
import {Check, CircleAlert, Database, FolderOpen, LoaderCircle, ScanLine, ShieldCheck} from 'lucide-react'
import * as m from 'motion/react-m'
import type {DesktopStatus} from './status.js'
import {elapsedLabel, startupStep} from './status.js'
import {UpdatePrompt} from './updates/UpdatePrompt.js'

export function WaitingTime({active = true}: {active?: boolean}) {
    const [elapsed, setElapsed] = useState(0)
    useEffect(() => {
        if (!active) return
        const started = Date.now()
        const timer = setInterval(() => setElapsed(Math.floor((Date.now() - started) / 1000)), 1000)
        return () => clearInterval(timer)
    }, [active])
    return <span className="waiting-time">Czas oczekiwania: {elapsedLabel(elapsed)}</span>
}

export function StartupScreen({status}: {status: DesktopStatus | null}) {
    const current = startupStep(status)
    const steps = [{icon: Database, title: 'Lokalna baza', detail: 'Przygotowanie zapisanych danych'},
        {icon: FolderOpen, title: 'Twój katalog', detail: 'Uruchomienie i sprawdzenie aplikacji'}]
    return <main className="desktop-startup">
        <m.section className="startup-card" initial={{opacity: 0, y: 12}} animate={{opacity: 1, y: 0}} aria-labelledby="startup-title">
            <div className="startup-topline"><div className="startup-brand"><span className="brand-mark"><ScanLine size={24}/></span><span>ScreenSort</span></div><UpdatePrompt/></div>
            <div className="startup-orbit" aria-hidden="true"><span/><span/><span/><ScanLine size={34}/></div>
            <p className="eyebrow">WSZYSTKO ZOSTAJE U CIEBIE</p>
            <h1 id="startup-title">Za chwilę<br/>jesteś u siebie.</h1>
            <p className="startup-intro">Otwieramy lokalny katalog. Ciężkie modele nie są teraz ładowane.</p>
            <ol className="startup-steps" aria-label="Etapy uruchamiania">
                {steps.map(({icon: StepIcon, title, detail}, index) => <li key={title} data-state={index < current ? 'done' : index === current ? status?.error ? 'error' : 'active' : 'pending'}>
                    <span className="startup-step-icon">{index < current ? <Check size={19}/> : index === current && status?.error ? <CircleAlert size={19}/> : <StepIcon size={19}/>}</span>
                    <span><strong>{title}</strong><small>{detail}</small></span>
                    {index === current && !status?.error && <LoaderCircle size={18} className="spin"/>}
                </li>)}
            </ol>
            {status?.error ? <div className="feedback feedback-error" role="alert"><CircleAlert size={19}/><div><strong>Nie udało się otworzyć katalogu</strong>
                <p>{status.error}</p><small>Nie usuwaj danych aplikacji. Szczegóły są w lokalnych logach.</small></div></div>
                : <div className="startup-wait" role="status"><span>{status?.phase ?? 'Łączymy się z aplikacją…'}</span><WaitingTime/></div>}
            <p className="startup-private"><ShieldCheck size={16}/>Twoje zdjęcia nie opuszczają komputera.</p>
        </m.section>
    </main>
}
