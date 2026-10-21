export type DesktopStatus = {phase: string; backendReady: boolean; analysisReady: boolean; analysisStarting: boolean; error: string | null}

// Native API currently exposes phase text, not step percentages. Do not invent progress.
export function startupStep(status: DesktopStatus | null): number {
    if (status?.backendReady) return 2
    if (status && /backend|migrac/i.test(status.phase)) return 1
    return 0
}

export function elapsedLabel(seconds: number): string {
    const whole = Math.max(0, Math.floor(seconds))
    return whole < 60 ? `${whole} s` : `${Math.floor(whole / 60)} min ${whole % 60} s`
}
