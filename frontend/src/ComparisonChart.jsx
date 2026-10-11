import React from 'react'

const STARTING_USD = 10000
const LINES = [
  { key: 'traditional', label: 'Reguły BREAKOUT_20_V1', color: '#0c7954' },
  { key: 'ai', label: 'Model AI', color: '#7257ae', dash: '8 5' },
  { key: 'CSPX', label: 'S&P 500 ETF Acc · CSPX', color: '#cc7552' },
  { key: 'IWDA', label: 'MSCI World ETF Acc · IWDA', color: '#3476a3' },
]

const usd = value => value == null ? '—' : `${new Intl.NumberFormat('pl-PL', {
  minimumFractionDigits: 2, maximumFractionDigits: 2,
}).format(value)} USD`

export function comparisonRows(comparison, valuations, benchmarks) {
  if (!comparison) return []
  const ids = {
    [comparison.traditionalPortfolioId]: 'traditional',
    [comparison.aiPortfolioId]: 'ai',
  }
  const byDate = new Map()
  // API returns newest revisions first. Keep one mark per strategy and session.
  for (const mark of valuations) {
    if (mark.comparisonId !== comparison.comparisonId) continue
    const key = ids[mark.portfolioId]
    const value = Number(mark.equityUsd)
    if (!key || !Number.isFinite(value)) continue
    if (!byDate.has(mark.session)) byDate.set(mark.session, {})
    const row = byDate.get(mark.session)
    if (row[key] === undefined) row[key] = value
  }
  const first = benchmarks?.commonStart
  for (const point of benchmarks?.points ?? []) {
    const value = Number(point.equityUsd)
    if (!Number.isFinite(value) || (first && point.session < first)) continue
    if (!byDate.has(point.session)) byDate.set(point.session, {})
    byDate.get(point.session)[point.fund] = value
  }
  const rows = [...byDate.entries()].filter(([date]) => !first || date >= first)
    .sort(([a], [b]) => a.localeCompare(b)).map(([date, values]) => ({ date, ...values }))
  if (first) {
    for (const key of ['traditional', 'ai']) {
      const baseline = rows.find(row => row[key] !== undefined)?.[key]
      if (baseline > 0) rows.forEach(row => {
        if (row[key] !== undefined) row[key] = STARTING_USD * row[key] / baseline
      })
    }
  }
  return rows
}

export default function ComparisonChart({ comparison, valuations, benchmarks, busy, onRefresh }) {
  const rows = comparisonRows(comparison, valuations, benchmarks)
  const paperStart = benchmarks?.paperStart
  const values = [STARTING_USD, ...rows.flatMap(row => LINES.map(line => row[line.key])
    .filter(value => value !== undefined))]
  const minimum = Math.min(...values)
  const maximum = Math.max(...values)
  const padding = Math.max(50, (maximum - minimum) * 0.14, maximum * 0.005)
  const lower = Math.max(0, minimum - padding)
  const upper = maximum + padding
  const left = 94, right = 875, top = 22, bottom = 252
  const x = index => rows.length === 1 ? (left + right) / 2 : left + (right - left) * index / (rows.length - 1)
  const y = value => bottom - (value - lower) / (upper - lower) * (bottom - top)
  const labels = rows.length <= 6 ? rows.map((_, index) => index)
    : [0, Math.floor((rows.length - 1) / 2), rows.length - 1]
  const latest = rows.at(-1)

  return <section className="panel" aria-labelledby="chart-title">
    <div className="panel-top">
      <div>
        <h2 id="chart-title">Ostatni tydzień i portfele paper</h2>
        <p className="muted">Przerywane fragmenty ETF-ów pokazują retrospekcję tygodnia przed startem portfeli paper. Reguły i AI pojawiają się dopiero od dnia, w którym zapisaliśmy ich decyzje. Nie porównujemy wcześniejszych wyników ETF z nieistniejącymi wtedy transakcjami paper.</p>
      </div>
      <button type="button" disabled={busy || !comparison || !valuations.length} onClick={onRefresh}>Odśwież ETF-y</button>
    </div>
    <div className="chart-legend" aria-label="Serie wykresu">
      {LINES.map(line => <span key={line.key} style={{ '--line-color': line.color }}>{line.label}</span>)}
    </div>
    {!rows.length ? <p className="status">Po pierwszym dziennym cyklu zobaczysz zapisane wyceny. ETF-y wymagają jeszcze pobrania cen giełdowych z EODHD.</p> : <>
      <div className="chart-frame">
        <svg viewBox="0 0 900 310" role="img" aria-label={`Wartość porównywanych portfeli od ${rows[0].date} do ${latest.date}`}>
          <text x="10" y="17" fill="#536e5f" fontSize="12">USD</text>
          {Array.from({ length: 5 }, (_, tick) => {
            const value = lower + (upper - lower) * tick / 4
            return <g key={tick}><line x1={left} x2={right} y1={y(value)} y2={y(value)} stroke="#e2e9e3" />
              <text x={left - 10} y={y(value) + 4} textAnchor="end" fill="#536e5f" fontSize="13">{Math.round(value).toLocaleString('pl-PL')}</text></g>
          })}
          {STARTING_USD >= lower && STARTING_USD <= upper && <line x1={left} x2={right} y1={y(STARTING_USD)} y2={y(STARTING_USD)} stroke="#91a59a" strokeDasharray="5 5" />}
          {paperStart && rows.some(row => row.date === paperStart) && <g>
            <line x1={x(rows.findIndex(row => row.date === paperStart))} x2={x(rows.findIndex(row => row.date === paperStart))} y1={top} y2={bottom} stroke="#7f9286" strokeDasharray="4 4" />
            <text x={x(rows.findIndex(row => row.date === paperStart))} y={top + 12} textAnchor="end" fill="#536e5f" fontSize="11">start paper</text>
          </g>}
          {[...new Set(labels)].map(index => <text key={index} x={x(index)} y="281" textAnchor={index === 0 ? 'start' : index === rows.length - 1 ? 'end' : 'middle'} fill="#536e5f" fontSize="13">{rows[index].date}</text>)}
          {LINES.map(line => {
            const pathFor = predicate => {
              let path = ''
              let previous = false
              rows.forEach((row, index) => {
                if (row[line.key] === undefined || !predicate(row)) { previous = false; return }
                path += `${previous ? ' L' : ' M'} ${x(index).toFixed(2)} ${y(row[line.key]).toFixed(2)}`
                previous = true
              })
              return path
            }
            const passive = line.key === 'CSPX' || line.key === 'IWDA'
            return <g key={line.key}>
              {passive && paperStart && <path d={pathFor(row => row.date <= paperStart)} fill="none" stroke={line.color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" strokeDasharray="6 5" opacity="0.7" />}
              <path d={pathFor(row => !passive || !paperStart || row.date >= paperStart)} fill="none" stroke={line.color} strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" strokeDasharray={line.dash} />
              {rows.map((row, index) => row[line.key] === undefined ? null : <circle key={row.date} cx={x(index)} cy={y(row[line.key])} r="4" fill="white" stroke={line.color} strokeWidth="2"><title>{line.label} · {row.date}: {usd(row[line.key])}</title></circle>)}
            </g>
          })}
        </svg>
      </div>
      <p className="muted">Ostatnia data: {latest.date}. {LINES.map(line => `${line.label}: ${usd(latest[line.key])}`).join(' · ')}</p>
      <details className="chart-details"><summary>Dokładne wyceny w tabeli</summary>
        <div className="table-wrap"><table className="chart-table"><thead><tr><th>Sesja</th>{LINES.map(line => <th key={line.key}>{line.label}</th>)}</tr></thead>
          <tbody>{[...rows].reverse().map(row => <tr key={row.date}><td>{row.date}</td>{LINES.map(line => <td key={line.key}>{usd(row[line.key])}</td>)}</tr>)}</tbody></table></div>
      </details>
    </>}
    <p className="muted chart-note">ETF-y akumulujące: CSSPX.SW (CSPX, IE00B5BMR087) i SWDA.SW (IWDA, IE00B4L5Y983), notowania USD na giełdzie SIX z EODHD. To ceny zamknięcia, nie NAV. Historyczna symulacja zaczyna się od 10 000 USD; rzeczywista cena transakcji mogłaby być inna. Portfele aktywne startują osobno od 10 000 USD w dniu {paperStart || 'pierwszej wyceny'}. {benchmarks?.note}</p>
  </section>
}
