import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import AuthScreen from '../../../src/views/AuthScreen'
import { api } from '../../../src/api/client'

describe('AuthScreen', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the sign-in form first', () => {
    render(<AuthScreen onAuthenticated={() => {}} />)

    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows an error passed in from a previous session', () => {
    render(<AuthScreen onAuthenticated={() => {}} initialError="Your session expired." />)

    expect(screen.getByRole('alert')).toHaveTextContent('Your session expired.')
  })

  it('switches to registration and back', async () => {
    const user = userEvent.setup()
    render(<AuthScreen onAuthenticated={() => {}} />)

    await user.click(screen.getByRole('button', { name: 'Need an account? Register' }))
    expect(screen.getByRole('heading', { name: 'Join the workspace' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Already have an account? Sign in' }))
    expect(screen.getByRole('heading', { name: 'Welcome back' })).toBeInTheDocument()
  })

  it('passes the access token on after a successful sign-in', async () => {
    const user = userEvent.setup()
    const onAuthenticated = vi.fn()
    const login = vi.spyOn(api, 'login').mockResolvedValue({ accessToken: 'token-123' } as Awaited<ReturnType<typeof api.login>>)
    render(<AuthScreen onAuthenticated={onAuthenticated} />)

    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct-horse')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(login).toHaveBeenCalledWith('ada@example.com', 'correct-horse')
    expect(onAuthenticated).toHaveBeenCalledWith('token-123')
  })

  it('shows the server error when sign-in fails', async () => {
    const user = userEvent.setup()
    vi.spyOn(api, 'login').mockRejectedValue(new Error('Invalid email or password.'))
    render(<AuthScreen onAuthenticated={() => {}} />)

    await user.type(screen.getByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
  })
})
