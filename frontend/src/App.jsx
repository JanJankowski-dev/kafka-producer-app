import React, { useCallback, useEffect, useState } from 'react'
import ComparisonChart from './ComparisonChart.jsx'

const reasonText = {
  ABOVE_PREVIOUS_20_SESSION_HIGH: 'Zamknięcie przebiło maksimum poprzednich 20 sesji.',
  MAX_HOLDING_PERIOD: 'Upłynął maksymalny czas utrzymywania pozycji.',
  BELOW_10_SESSION_AVERAGE: 'Zamknięcie spadło poniżej średniej z 10 sesji.',
  NO_BREAKOUT: 'Brak wybicia ponad maksimum z 20 sesji.',
  INSUFFICIENT_ENTRY_HISTORY: 'Za mało danych do oceny.',
  NO_CLOSE_FOR_SESSION: 'Brak zamknięcia dla tej sesji.',
}

async function getJson(url) {
  const response = await fetch(url)
  return response.ok ? response.json() : null
}

async function postJson(url) {
  const response = await fetch(url, { method: 'POST' })
  if (!response.ok) {
    const error = await response.json().catch(() => ({}))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  return response.json()
}

export default function App() {
  const [data, setData] = useState({})
  const [busy, setBusy] = useState('')
  const [message, setMessage] = useState('Ładowanie danych…')
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    try {
      const [universe, status] = await Promise.all([
        getJson('/market-data/universe'), getJson('/market-data/status'),
      ])
      if (!universe || !status) throw new Error('Lokalny tryb danych nie jest dostępny.')
      const [signals, history, comparison, valuations, benchmarks, activity] = status.latestImport
        ? await Promise.all([
          getJson('/market-screenings/latest'), getJson('/market-screenings/history'),
          getJson('/strategy-comparisons/latest'), getJson('/daily-research/valuations'),
          getJson('/etf-benchmarks'), getJson('/private/paper-activity'),
        ]) : [null, null, null, [], null, null]
      setData({ universe, status, signals, history, comparison, valuations: valuations ?? [], benchmarks, activity })
      setMessage(!status.apiKeyConfigured
        ? 'Brak klucza EODHD. Ustaw EODHD_API_KEY lokalnie; bez niego nie pokażemy fikcyjnych cen.'
        : status.latestImport
          ? `Zaimportowano ${status.latestImport.instrumentCount} spółek i ${status.latestImport.sessionCount} wspólnych sesji. Ostatnia: ${status.latestImport.latestCommonSession}.`
          : 'Klucz EODHD gotowy. Pobierz pierwszą historię cen.')
      setError('')
    } catch (exception) {
      setError(exception.message)
    }
  }, [])

  useEffect(() => { load() }, [load])

  async function action(name, url) {
    setBusy(name)
    setError('')
    try {
      await postJson(url)
      await load()
    } catch (exception) {
      setError(exception.message)
    } finally {
      setBusy('')
    }
  }

  const { universe, status, signals, history, comparison, valuations = [], benchmarks, activity } = data
  const signalMap = new Map((signals?.instruments ?? []).map(item => [item.symbol, item.signal]))
  const recentSignals = history?.buyObservations ?? []
  const latestMarks = valuations.filter(item => item.marketImportBatchId === status?.latestImport?.batchId)

  return <main className="shell">
    <header><div className="brand"><span aria-hidden="true">↗</span>Investment Portfolio Lab</div><div className="tag">Badanie strategii · tylko paper trading</div></header>
    <section className="hero"><div className="eyebrow">S&P 500 · rzeczywiste spółki</div>
      <h1>Czy aktywne strategie pobiją proste ETF-y?</h1>
      <p>Porównujemy reguły i decyzje AI z pasywnym S&P 500 oraz MSCI World. Obserwujemy 10 największych składników S&P 500 według listy z 31 sierpnia 2026 r. To eksperyment edukacyjny, nie rekomendacja inwestycyjna.</p>
    </section>

    <section className="panel" aria-labelledby="data-title"><div className="panel-top"><div>
      <h2 id="data-title">Dane rynkowe</h2>
      <p className="muted">Dzienne ceny 10 spółek i dwóch ETF-ów z EODHD. ETF-y: notowania giełdowe w USD, nie NAV emitenta.</p>
    </div><div className="tag">{status?.latestImport?.latestCommonSession || 'Oczekuje na import'}</div></div>
      <div className="actions"><button disabled={!!busy || !status?.apiKeyConfigured} onClick={() => action('import', '/market-data/imports')}>Pobierz ceny 10 spółek</button>
        <button disabled={!!busy} onClick={load}>Odśwież widok</button></div>
      <div className={`status ${error ? 'error' : !status?.apiKeyConfigured ? 'warn' : ''}`} role="status">{error || (busy ? 'Trwa pobieranie i zapisywanie danych…' : message)}</div>
    </section>

    <section className="section-gap" aria-labelledby="instruments-title"><h2 id="instruments-title">Obserwowana dziesiątka</h2>
      <p className="muted">{signals?.asOfSession ? `Sygnały BREAKOUT_20_V1 po sesji ${signals.asOfSession}.` : 'Czekamy na historię cen.'}</p>
      <div className="grid">{(universe?.instruments ?? []).map(instrument => {
        const signal = signalMap.get(instrument.symbol)
        return <article className="card" key={instrument.symbol}><div className="card-top"><div><div className="symbol">{instrument.symbol}</div><div className="name">{instrument.name}</div></div>
          <span className={`pill ${signal?.action || 'UNAVAILABLE'}`}>{signal?.action || 'BRAK CEN'}</span></div>
          {signal ? <><div className="price">{Number(signal.latestAdjustedClose).toLocaleString('pl-PL', { maximumFractionDigits: 2 })} USD <small>· skorygowane zamknięcie</small></div>
            <div className="reason">{reasonText[signal.reasonCode] || signal.reasonCode}</div></>
            : <div className="reason">Historia cen nie została jeszcze zaimportowana.</div>}</article>
      })}</div>
    </section>

    <section className="panel" aria-labelledby="history-title"><h2 id="history-title">Wcześniejsze sygnały</h2>
      <p className="muted">{history ? `${recentSignals.length} sygnałów BUY w ${history.sessionCount} sesjach. Pokazujemy 10 najnowszych.` : 'Po imporcie pokażemy wcześniejsze wybicia.'}</p>
      <div className="history-list">{recentSignals.slice(0, 10).map((item, index) => <div className="history-row" key={`${item.symbol}-${item.session}-${index}`}><strong>{item.symbol}</strong><span>{item.session}</span>
        <span>{item.forwardAdjustedCloseReturnPct == null ? '5 sesji jeszcze nie minęło' : `${Number(item.forwardAdjustedCloseReturnPct).toFixed(2)}% po 5 sesjach`}</span></div>)}</div>
      <p className="muted footnote">To obserwacja po sygnale, nie wynik wykonanej transakcji.</p>
    </section>

    <section className="panel" aria-labelledby="comparison-title"><h2 id="comparison-title">Aktywne portfele paper</h2>
      <p className="muted">Reguły i model AI dostają po 10 000 wirtualnych USD. Decyzje po sesji; zlecenia są rozliczane po otwarciu następnej dostępnej sesji. Do czasu potwierdzenia licencji EODHD tor AI wybiera HOLD i nie wysyła danych do OpenAI.</p>
      <div className="actions"><button disabled={!!busy || !status?.latestImport} onClick={() => action('comparison', '/strategy-comparisons')}>Utwórz parę portfeli</button></div>
      <div className="status">{comparison ? `Reguły: ${comparison.traditionalPortfolioId} · AI: ${comparison.aiPortfolioId}` : 'Najpierw zaimportuj ceny i utwórz portfele.'}</div>
    </section>

    <section className="panel" aria-labelledby="daily-title"><h2 id="daily-title">Codzienny przegląd</h2>
      <p className="muted">Aplikacja sprawdza dane od poniedziałku do soboty o 09:00 czasu Warszawy. Ponowienie tej samej sesji nie duplikuje decyzji ani zleceń. Wycena nie zawiera prowizji, spreadu ani poślizgu.</p>
      <div className="actions"><button disabled={!!busy || !status?.apiKeyConfigured} onClick={() => action('daily', '/daily-research/run')}>Sprawdź teraz</button></div>
      <div className="status">{latestMarks.length >= 2 ? `Sesja ${latestMarks[0].session}: zapisano wyceny obu aktywnych portfeli.` : 'Brak wyceny dla ostatniego importu.'}</div>
    </section>

    <ComparisonChart comparison={comparison} valuations={valuations} benchmarks={benchmarks} busy={!!busy}
      onRefresh={() => action('benchmarks', '/etf-benchmarks/refresh')} />

    <section className="panel" aria-labelledby="activity-title"><h2 id="activity-title">Dziennik decyzji paper</h2>
      <p className="muted">„Oczekuje” nie oznacza wykonanej transakcji. Szczegóły pojawią się w prywatnym trybie z uwierzytelnieniem.</p>
      <div className="history-list">{(activity?.decisions ?? []).slice(0, 20).map((item, index) => <div className="history-row" key={`decision-${index}`}><strong>{item.engine === 'AI' ? 'AI' : 'Reguły'}</strong><span>{item.symbol} {item.action}</span><span>{item.session}: {item.reason}</span></div>)}
        {(activity?.orders ?? []).slice(0, 10).map((item, index) => <div className="history-row" key={`order-${index}`}><strong>Zlecenie</strong><span>{item.symbol} {item.side}</span><span>{item.status} · decyzja {item.requestedSession}{item.executionSession ? ` · wykonanie ${item.executionSession} po ${item.fillPrice} USD` : ''}</span></div>)}</div>
    </section>

    <p className="note">Dane historyczne nie są cenami czasu rzeczywistego. Lista obecnych liderów użyta wstecznie powoduje survivorship bias. Wynik bez kosztów, spreadu, poślizgu i pełnej obsługi zdarzeń korporacyjnych nie dowodzi możliwości zysku. Źródła: <a href="https://www.spglobal.com/spdji/en/indices/equity/sp-500/">S&P Dow Jones Indices</a>, <a href="https://eodhd.com/financial-apis/api-for-historical-data-and-volumes">EODHD</a>, <a href="https://www.ishares.com/ch/individual/en/products/253743/ishares-sp-500-b-ucits-etf-acc-fun">iShares CSPX/CSSPX</a>, <a href="https://www.ishares.com/ch/individual/en/products/251882/ishares-msci-world-ucits-etf-acc-fund">iShares IWDA/SWDA</a>.</p>
  </main>
}
