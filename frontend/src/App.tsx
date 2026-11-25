import { useCallback, useEffect, useMemo, useState } from 'react'
import {
  ArrowDownLeft,
  ArrowLeftRight,
  ArrowRight,
  ArrowUpRight,
  BadgeCheck,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Copy,
  CreditCard,
  Eye,
  EyeOff,
  House,
  Landmark,
  LoaderCircle,
  LogOut,
  Plus,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  WalletCards,
  X,
} from 'lucide-react'

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const TOKEN_KEY = 'digital-wallet-token'

type Account = {
  id: string
  accountNumber: string
  balance: string
  currency: string
  status: 'ACTIVE' | 'FROZEN'
}

type Transaction = {
  id: string
  type: 'DEPOSIT' | 'TRANSFER'
  amount: string
  currency: string
  status: 'COMPLETED' | 'FAILED'
  createdAt: string
  destinationAccountNumber: string | null
}

type Page<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

type ApiError = { message?: string; code?: string; fieldErrors?: Record<string, string> }
type AuthMode = 'login' | 'register'
type DialogMode = 'deposit' | 'transfer' | null

async function api<T>(path: string, token?: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (token) headers.set('Authorization', `Bearer ${token}`)
  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    const error = body as ApiError | null
    const fields = error?.fieldErrors ? Object.values(error.fieldErrors).join(' ') : ''
    throw new Error([error?.message || `Request failed (${response.status})`, fields].filter(Boolean).join(' '))
  }
  return body as T
}

function money(value: string, currency = 'INR') {
  const amount = Number(value)
  if (!Number.isFinite(amount)) return `${currency} ${value}`
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency, minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(amount)
}

function dateLabel(value: string) {
  return new Intl.DateTimeFormat('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(value))
}

function newIdempotencyKey() {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem(TOKEN_KEY) || '')
  const [account, setAccount] = useState<Account | null>(null)
  const [history, setHistory] = useState<Page<Transaction> | null>(null)
  const [page, setPage] = useState(0)
  const [activeView, setActiveView] = useState<'overview' | 'activity'>('overview')
  const [dialog, setDialog] = useState<DialogMode>(null)
  const [toast, setToast] = useState('')
  const [loading, setLoading] = useState(Boolean(token))
  const [refreshing, setRefreshing] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [copied, setCopied] = useState(false)

  const loadDashboard = useCallback(async (currentToken: string, requestedPage = 0, quiet = false) => {
    if (quiet) setRefreshing(true)
    else setLoading(true)
    setLoadError('')
    try {
      const [wallet, transactions] = await Promise.all([
        api<Account>('/accounts/me', currentToken),
        api<Page<Transaction>>(`/transactions/history?page=${requestedPage}&size=8`, currentToken),
      ])
      setAccount(wallet)
      setHistory(transactions)
      setPage(requestedPage)
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not load your wallet.'
      setLoadError(message)
      if (message.toLowerCase().includes('authentication') || message.toLowerCase().includes('token')) {
        signOut()
      }
    } finally {
      setLoading(false)
      setRefreshing(false)
    }
  }, [])

  const signOut = useCallback(() => {
    sessionStorage.removeItem(TOKEN_KEY)
    setToken('')
    setAccount(null)
    setHistory(null)
    setDialog(null)
    setActiveView('overview')
  }, [])

  useEffect(() => {
    if (token) void loadDashboard(token, page)
    else setLoading(false)
  }, [token, page, loadDashboard])

  useEffect(() => {
    if (!toast) return
    const timer = window.setTimeout(() => setToast(''), 4200)
    return () => window.clearTimeout(timer)
  }, [toast])

  const recentTransactions = history?.content ?? []
  const hasActivity = (history?.totalElements ?? 0) > 0

  if (!token) {
    return <AuthScreen onAuthenticated={(value) => {
      sessionStorage.setItem(TOKEN_KEY, value)
      setToken(value)
      setPage(0)
    }} />
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#home" onClick={() => setActiveView('overview')} aria-label="Digital Wallet home">
          <span className="brand-mark"><WalletCards size={21} strokeWidth={2.2} /></span>
          <span>vault<span className="brand-period">.</span></span>
        </a>

        <div className="sidebar-label">WORKSPACE</div>
        <nav className="main-nav" aria-label="Main navigation">
          <button className={activeView === 'overview' ? 'nav-item active' : 'nav-item'} onClick={() => setActiveView('overview')}>
            <House size={18} /> <span>Overview</span>
          </button>
          <button className={activeView === 'activity' ? 'nav-item active' : 'nav-item'} onClick={() => setActiveView('activity')}>
            <ArrowLeftRight size={18} /> <span>Activity</span>
          </button>
        </nav>

        <div className="sidebar-bottom">
          <div className="safe-card">
            <div className="safe-icon"><ShieldCheck size={18} /></div>
            <strong>Your wallet, protected</strong>
            <p>Every transfer is secured and recorded in your ledger.</p>
          </div>
          <button className="profile-button" onClick={signOut}>
            <span className="avatar">{account?.accountNumber.slice(-2) || 'DW'}</span>
            <span className="profile-copy"><strong>My account</strong><small>Sign out</small></span>
            <LogOut size={17} className="profile-logout" />
          </button>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="mobile-brand"><span className="brand-mark"><WalletCards size={19} /></span><strong>vault<span className="brand-period">.</span></strong></div>
          <div className="breadcrumb"><span>Workspace</span><ChevronRight size={15} /><strong>{activeView === 'overview' ? 'Overview' : 'Activity'}</strong></div>
          <div className="topbar-right">
            <span className="secure-pill"><span className="online-dot" /> Secure session</span>
            <button className="icon-button" onClick={() => void loadDashboard(token, page, true)} aria-label="Refresh wallet" title="Refresh">
              <RefreshCw size={17} className={refreshing ? 'spin' : ''} />
            </button>
          </div>
        </header>

        {loading ? <LoadingState /> : loadError ? (
          <section className="page-wrap"><div className="error-panel"><CircleHelp size={22} /><div><strong>We couldn’t load your wallet</strong><p>{loadError}</p></div><button className="button button-dark" onClick={() => void loadDashboard(token, page)}>Try again</button></div></section>
        ) : activeView === 'activity' ? (
          <ActivityView
            transactions={recentTransactions}
            page={history?.page ?? 0}
            totalPages={history?.totalPages ?? 0}
            totalElements={history?.totalElements ?? 0}
            onPageChange={setPage}
            currency={account?.currency ?? 'INR'}
          />
        ) : (
          <section className="page-wrap">
            <div className="welcome-row">
              <div>
                <div className="eyebrow"><span className="eyebrow-line" /> YOUR MONEY, IN ONE PLACE</div>
                <h1>Your wallet overview</h1>
                <p className="page-subtitle">A clear view of your balance and recent activity.</p>
              </div>
              <div className="account-chip"><span className="account-chip-icon"><CreditCard size={16} /></span><span><small>ACCOUNT NUMBER</small><strong>{account?.accountNumber}</strong></span><button className="copy-button" onClick={() => void copyAccount()} aria-label="Copy account number">{copied ? <Check size={16} /> : <Copy size={15} />}</button></div>
            </div>

            <div className="hero-card">
              <div className="hero-glow" />
              <div className="hero-topline"><span className="hero-label"><span className="hero-dot" /> AVAILABLE BALANCE</span><span className="account-status"><BadgeCheck size={15} /> {account?.status === 'FROZEN' ? 'Account frozen' : 'Account active'}</span></div>
              <div className="balance-value">{money(account?.balance ?? '0', account?.currency)}</div>
              <div className="hero-bottom"><span>Balance in {account?.currency || 'INR'}</span><span className="hero-card-brand"><span>V</span> vault.</span></div>
              <div className="hero-pattern pattern-one" /><div className="hero-pattern pattern-two" />
            </div>

            <div className="quick-actions">
              <button className="action-card" onClick={() => setDialog('deposit')}>
                <span className="action-icon deposit-icon"><ArrowDownLeft size={20} /></span>
                <span className="action-copy"><strong>Add test funds</strong><small>Simulated deposit</small></span>
                <ArrowRight className="action-arrow" size={17} />
              </button>
              <button className="action-card" onClick={() => setDialog('transfer')}>
                <span className="action-icon transfer-icon"><ArrowUpRight size={20} /></span>
                <span className="action-copy"><strong>Send money</strong><small>Transfer to a wallet</small></span>
                <ArrowRight className="action-arrow" size={17} />
              </button>
            </div>

            <div className="section-heading"><div><div className="section-kicker">YOUR ACCOUNT</div><h2>Recent activity</h2></div><button className="text-button" onClick={() => setActiveView('activity')}>View all <ArrowRight size={15} /></button></div>
            <TransactionList transactions={recentTransactions.slice(0, 5)} currency={account?.currency ?? 'INR'} hasActivity={hasActivity} />

            <div className="demo-note"><Sparkles size={15} /><span>Deposits in this project are simulated. No real money is moved.</span></div>
          </section>
        )}
      </main>

      {dialog && account && <MoneyDialog mode={dialog} account={account} token={token} onClose={() => setDialog(null)} onSuccess={(message) => {
        setDialog(null)
        setToast(message)
        setActiveView('overview')
        void loadDashboard(token, 0, true)
      }} />}
      {toast && <div className="toast" role="status"><span className="toast-check"><Check size={15} /></span>{toast}<button aria-label="Dismiss message" onClick={() => setToast('')}><X size={15} /></button></div>}
    </div>
  )

  async function copyAccount() {
    if (!account) return
    try {
      await navigator.clipboard.writeText(account.accountNumber)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1600)
    } catch {
      setToast('Your account number is ' + account.accountNumber)
    }
  }
}

function AuthScreen({ onAuthenticated }: { onAuthenticated: (token: string) => void }) {
  const [mode, setMode] = useState<AuthMode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (mode === 'register' && new TextEncoder().encode(password).length > 72) {
        throw new Error('Password must be 72 bytes or fewer.')
      }
      const result = await api<{ token: string }>(`/auth/${mode === 'register' ? 'register' : 'login'}`, undefined, {
        method: 'POST', body: JSON.stringify({ email: email.trim(), password }),
      })
      onAuthenticated(result.token)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Unable to sign you in. Please try again.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-left">
        <a className="brand auth-brand" href="#home"><span className="brand-mark"><WalletCards size={21} /></span><span>vault<span className="brand-period">.</span></span></a>
        <div className="auth-message">
          <span className="auth-brow"><span className="online-dot" /> YOUR MONEY, MADE CLEAR</span>
          <h1>Make every<br />move <em>count.</em></h1>
          <p>Send money simply. Keep track of every transaction. Your wallet, always within reach.</p>
          <div className="auth-visual" aria-hidden="true">
            <div className="visual-card visual-back"><div className="visual-mini-mark">V</div><span className="visual-dots">•••• &nbsp;•••• &nbsp;••••</span></div>
            <div className="visual-card visual-front"><div className="visual-card-top"><span>SAMPLE BALANCE</span><WalletCards size={18} /></div><strong>₹ 0<span>.00</span></strong><div className="visual-card-bottom"><span>VIRTUAL WALLET</span><span>•• 2048</span></div></div>
            <div className="visual-float"><span className="float-icon"><ArrowUpRight size={17} /></span><span><small>SIMULATED TRANSFER</small><strong>₹ 2,500.00</strong></span><BadgeCheck size={15} className="float-check" /></div>
          </div>
        </div>
        <div className="auth-footer"><span>© 2026 Vault Wallet</span><span><ShieldCheck size={14} /> Protected with secure sign-in</span></div>
      </div>
      <div className="auth-right">
        <div className="mobile-brand auth-mobile-brand"><span className="brand-mark"><WalletCards size={19} /></span><strong>vault<span className="brand-period">.</span></strong></div>
        <div className="auth-form-wrap">
          <div className="auth-step">{mode === 'login' ? 'WELCOME BACK' : 'GET STARTED'}</div>
          <h2>{mode === 'login' ? 'Sign in to your wallet' : 'Create your wallet'}</h2>
          <p className="auth-form-subtitle">{mode === 'login' ? 'Enter your details to access your account.' : 'It only takes a moment to get started.'}</p>
          <div className="auth-switch" role="tablist" aria-label="Authentication method">
            <button role="tab" aria-selected={mode === 'login'} className={mode === 'login' ? 'selected' : ''} onClick={() => { setMode('login'); setError('') }}>Sign in</button>
            <button role="tab" aria-selected={mode === 'register'} className={mode === 'register' ? 'selected' : ''} onClick={() => { setMode('register'); setError('') }}>Create account</button>
          </div>
          <form onSubmit={submit} className="auth-form">
            <label htmlFor="email">Email address</label>
            <input id="email" type="email" autoComplete="email" placeholder="you@example.com" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={254} />
            <div className="password-label"><label htmlFor="password">Password</label>{mode === 'login' && <span>Keep it private</span>}</div>
            <div className="password-wrap"><input id="password" type={showPassword ? 'text' : 'password'} autoComplete={mode === 'register' ? 'new-password' : 'current-password'} placeholder={mode === 'register' ? 'At least 12 characters' : 'Enter your password'} value={password} onChange={(event) => setPassword(event.target.value)} required minLength={mode === 'register' ? 12 : 1} maxLength={72} /><button type="button" onClick={() => setShowPassword((value) => !value)} aria-label={showPassword ? 'Hide password' : 'Show password'}>{showPassword ? <EyeOff size={17} /> : <Eye size={17} />}</button></div>
            {error && <div className="form-error" role="alert"><CircleHelp size={16} />{error}</div>}
            <button className="button button-primary auth-submit" type="submit" disabled={busy}>{busy ? <><LoaderCircle className="spin" size={18} /> Working…</> : <>{mode === 'login' ? 'Sign in to wallet' : 'Create account'}<ArrowRight size={17} /></>}</button>
          </form>
          <div className="auth-form-foot"><ShieldCheck size={15} /> Your connection is encrypted and secure</div>
          <div className="auth-disclosure">This is a demo wallet. Deposits are simulated and do not move real funds.</div>
        </div>
      </div>
    </div>
  )
}

function LoadingState() {
  return <section className="page-wrap loading-state"><div className="loading-pulse" /><div className="loading-copy"><strong>Loading your wallet</strong><span>Connecting securely to your account…</span></div></section>
}

function TransactionList({ transactions, currency, hasActivity }: { transactions: Transaction[]; currency: string; hasActivity: boolean }) {
  if (!hasActivity || transactions.length === 0) {
    return <div className="empty-state"><span className="empty-icon"><Landmark size={21} /></span><div><strong>Your activity will show up here</strong><p>When you send or add funds, you’ll see the details here.</p></div></div>
  }
  return <div className="transaction-list">
    {transactions.map((transaction) => {
      const isDeposit = transaction.type === 'DEPOSIT'
      return <div className="transaction-row" key={transaction.id}>
        <span className={isDeposit ? 'transaction-icon incoming' : 'transaction-icon outgoing'}>{isDeposit ? <ArrowDownLeft size={18} /> : <ArrowUpRight size={18} />}</span>
        <span className="transaction-main"><strong>{isDeposit ? 'Test funds added' : 'Wallet transfer'}</strong><small>{dateLabel(transaction.createdAt)}{transaction.destinationAccountNumber ? ` · To ${transaction.destinationAccountNumber}` : ''}</small></span>
        <span className={isDeposit ? 'transaction-amount positive' : 'transaction-amount'}>{isDeposit ? '+' : '−'}{money(transaction.amount, currency)}</span>
        <span className="transaction-status"><span className="status-dot" />{transaction.status === 'COMPLETED' ? 'Complete' : 'Failed'}</span>
      </div>
    })}
  </div>
}

function ActivityView({ transactions, page, totalPages, totalElements, onPageChange, currency }: {
  transactions: Transaction[]; page: number; totalPages: number; totalElements: number; onPageChange: (page: number) => void; currency: string
}) {
  return <section className="page-wrap">
    <div className="welcome-row activity-welcome"><div><div className="eyebrow"><span className="eyebrow-line" /> YOUR WALLET</div><h1>Transaction activity</h1><p className="page-subtitle">Every move, recorded in one place.</p></div><span className="activity-count">{totalElements} {totalElements === 1 ? 'transaction' : 'transactions'}</span></div>
    <div className="activity-card"><div className="activity-card-head"><div><div className="section-kicker">TRANSACTION HISTORY</div><h2>All activity</h2></div><span className="activity-filter"><span className="online-dot" /> All transactions</span></div>
      <TransactionList transactions={transactions} currency={currency} hasActivity={totalElements > 0} />
      {totalPages > 1 && <div className="pagination"><span>Page {page + 1} of {totalPages}</span><div><button aria-label="Previous page" disabled={page === 0} onClick={() => onPageChange(page - 1)}><ChevronLeft size={17} /></button><button aria-label="Next page" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)}><ChevronRight size={17} /></button></div></div>}
    </div>
    <div className="demo-note"><Sparkles size={15} /><span>Deposits shown here are simulated test transactions.</span></div>
  </section>
}

function MoneyDialog({ mode, account, token, onClose, onSuccess }: {
  mode: Exclude<DialogMode, null>; account: Account; token: string; onClose: () => void; onSuccess: (message: string) => void
}) {
  const [amount, setAmount] = useState('')
  const [destination, setDestination] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const isDeposit = mode === 'deposit'
  const validAmount = useMemo(() => /^\d{1,15}(\.\d{1,4})?$/.test(amount) && Number(amount) > 0, [amount])
  const validDestination = /^\d{12}$/.test(destination) && destination !== account.accountNumber

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      await api<Transaction>(`/transactions/${mode}`, token, {
        method: 'POST',
        headers: { 'Idempotency-Key': newIdempotencyKey() },
        body: JSON.stringify(isDeposit ? { amount } : { amount, destinationAccountNumber: destination }),
      })
      onSuccess(isDeposit ? 'Simulated funds added to your wallet.' : 'Your transfer is complete.')
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'The transaction could not be completed.')
    } finally {
      setBusy(false)
    }
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onClose() }}>
    <section className="money-modal" role="dialog" aria-modal="true" aria-labelledby="money-title">
      <div className="modal-head"><span className={isDeposit ? 'modal-icon deposit-icon' : 'modal-icon transfer-icon'}>{isDeposit ? <Plus size={20} /> : <ArrowUpRight size={20} />}</span><button className="icon-button modal-close" aria-label="Close dialog" onClick={onClose} disabled={busy}><X size={18} /></button></div>
      <div className="modal-eyebrow">{isDeposit ? 'ADD TO YOUR WALLET' : 'SEND A TRANSFER'}</div>
      <h2 id="money-title">{isDeposit ? 'Add test funds' : 'Send money'}</h2>
      <p className="modal-subtitle">{isDeposit ? 'This demo adds simulated funds. It does not charge a card or bank account.' : 'Transfers are processed securely between wallet accounts.'}</p>
      <div className="modal-balance"><span>Available balance</span><strong>{money(account.balance, account.currency)}</strong></div>
      <form onSubmit={submit} className="money-form">
        {mode === 'transfer' && <><label htmlFor="destination">Recipient account number</label><input id="destination" inputMode="numeric" autoComplete="off" placeholder="12-digit account number" value={destination} onChange={(event) => setDestination(event.target.value.replace(/\D/g, '').slice(0, 12))} required pattern="[0-9]{12}" /><small className="field-hint">Enter the recipient’s 12-digit wallet account number.</small></>}
        <label htmlFor="amount">Amount <span>{account.currency}</span></label>
        <div className="amount-input"><span>₹</span><input id="amount" inputMode="decimal" placeholder="0.00" value={amount} onChange={(event) => setAmount(event.target.value)} required maxLength={20} /><span className="amount-currency">{account.currency}</span></div>
        <small className="field-hint">Up to 4 decimal places.</small>
        {error && <div className="form-error" role="alert"><CircleHelp size={16} />{error}</div>}
        <button className="button button-primary modal-submit" disabled={busy || !validAmount || (mode === 'transfer' && !validDestination)}>{busy ? <><LoaderCircle className="spin" size={18} /> Processing…</> : <>{isDeposit ? 'Add simulated funds' : 'Review and send'}<ArrowRight size={17} /></>}</button>
      </form>
      <div className="modal-safety"><ShieldCheck size={14} /> Secured by encrypted wallet processing</div>
    </section>
  </div>
}

export default App
