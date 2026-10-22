import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// Find the empty root element in index.html and mount the React app inside it.
// StrictMode helps reveal some unsafe React patterns while developing; it does not add a visible UI layer.
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
