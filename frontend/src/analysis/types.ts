export type Step<T> =
    | {status: 'COMPLETED', result: T, error: null}
    | {status: 'FAILED', result: null, error: {code: string, message: string}}
    | {status: 'SKIPPED', result: null, error: null}

export type OcrResult = {text: string, engine: string, engine_version: string, duration_ms: number}
export type VisualResult = {description: string, model: string, model_revision: string,
    task: string, device: string, duration_ms: number}
export type SavedAnalysis = {id: number, fileId: number, analyzedAt: string, matchesCatalog: boolean,
    result: {source_sha256: string, ocr: Step<OcrResult>, visual: Step<VisualResult>}}
export type AnalysisJob = {id: string, fileId: number,
    status: 'PREPARING' | 'ANALYZING' | 'SAVING' | 'COMPLETED' | 'FAILED',
    startedAt: string, analysis: SavedAnalysis | null, errorMessage: string | null}
