// Runs before every frontend unit test: adds DOM matchers such as toBeInTheDocument()
// and unmounts rendered components between tests.
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})
