import {createRoot} from 'react-dom/client'
import {UpdateFixture} from './update-preview-ui'
import '../../src/index.css'
import '../../src/App.css'
import '../../src/desktop/DesktopStartup.css'
import '../../src/desktop/updates/updates.css'
const root = createRoot(document.getElementById('root')!)
root.render(<UpdateFixture/>)
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount())
