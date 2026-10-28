import {createRoot} from 'react-dom/client'
import {UpdateFixture} from './update-preview-ui.js'
import '../../src/app/index.css'
import '../../src/app/App.css'
import '../../src/tauri/DesktopStartup.css'
import '../../src/tauri/updates/updates.css'
const root = createRoot(document.getElementById('root')!)
root.render(<UpdateFixture/>)
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount())
