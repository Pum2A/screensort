import {ArrowRight, CircleAlert, FolderOpen, Images} from 'lucide-react'
import {CatalogGallery} from './CatalogGallery.js'
import {folderName} from './format.js'
import type {CatalogPageState} from './useCatalogPage.js'
import {Icon} from '../ui/Icon.js'

export function CatalogSection({catalog, hidden, scanning, onOrganize}: {
    catalog: CatalogPageState, hidden: boolean, scanning: boolean, onOrganize: () => void,
}) {
    const {page, loading, error} = catalog
    const pageCount = page ? Math.ceil(page.totalFiles / page.pageSize) : 0
    const rangeStart = page && page.files.length > 0 ? page.pageNumber * page.pageSize + 1 : 0
    const rangeEnd = page && rangeStart > 0 ? rangeStart + page.files.length - 1 : 0
    return <section hidden={hidden} id="catalog" aria-labelledby="catalog-title" className="catalog">
        <div className="catalog-heading"><div><span className="eyebrow">ZAPISANY KATALOG</span><h2 id="catalog-title"><FolderOpen size={21}/>{catalog.folder ? folderName(catalog.folder) : 'Twój pierwszy folder'}
            {page && <span className="count-badge">{page.totalFiles}</span>}</h2></div>
            <button className="text-button" onClick={onOrganize}>Analiza i propozycje<ArrowRight size={16}/></button></div>
        <CatalogGallery key={[catalog.folder, catalog.pageNumber, catalog.generation].join(':')} files={loading || error ? [] : page?.files ?? []}>
            {loading ? <div className="catalog-skeleton" role="status"><span className="sr-only">Ładujemy katalog obrazów</span>{Array.from({length: 8}, (_, index) => <div className="skeleton-tile" key={index}><span/><i/><i/></div>)}</div>
                : error ? <div className="catalog-empty catalog-empty-error"><CircleAlert size={30}/><h3>Katalog jest niedostępny</h3><p role="alert">{error}</p>
                    <button className="secondary-button" onClick={catalog.retry}><Icon name="refresh"/>Spróbuj ponownie</button></div>
                : !page || page.files.length === 0 ? <div className="catalog-empty"><Images size={36}/><h3>{page?.totalFiles === 0 ? 'Tu zaczyna się porządek.' : 'Wybierz folder na początek.'}</h3>
                    <p>Wybierz źródło i zeskanuj jego zawartość. Twoje obrazy zostają dokładnie tam, gdzie są.</p></div> : undefined}
        </CatalogGallery>
        {page && page.totalFiles > 0 && <nav className="catalog-pagination" aria-label="Strony katalogu"><span className="page-range">{rangeStart}–{rangeEnd} z {page.totalFiles} obrazów</span>
            <div className="page-controls"><button className="icon-button" aria-label="Poprzednia strona" disabled={loading || scanning || catalog.pageNumber === 0} onClick={() => catalog.changePage(-1)}><Icon name="left"/></button>
                <span aria-live="polite">{page.pageNumber + 1} / {pageCount}</span><button className="icon-button" aria-label="Następna strona" disabled={loading || scanning || !page.hasNext} onClick={() => catalog.changePage(1)}><Icon name="right"/></button></div></nav>}
    </section>
}
