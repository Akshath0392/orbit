import { describe, it, expect, afterEach } from 'vitest'
import { configureDatetime, parseServerDate, fmtDate, fmtTime, fmtDateTime, fmtDateTimeFull, relTime } from './datetime'

// Module state is shared — restore the defaults after every test.
afterEach(() => configureDatetime({ displayTimezone: 'Asia/Kolkata', serverTimezone: 'UTC' }))

describe('parseServerDate', () => {
  it('parses offset-aware ISO directly', () => {
    expect(parseServerDate('2026-07-25T10:31:02+05:30')!.toISOString()).toBe('2026-07-25T05:01:02.000Z')
    expect(parseServerDate('2026-07-25T10:31:02Z')!.toISOString()).toBe('2026-07-25T10:31:02.000Z')
  })

  it('interprets naive strings in the configured server zone', () => {
    configureDatetime({ serverTimezone: 'UTC' })
    expect(parseServerDate('2026-07-25T10:31:02')!.toISOString()).toBe('2026-07-25T10:31:02.000Z')
    expect(parseServerDate('2026-07-25 10:31:02')!.toISOString()).toBe('2026-07-25T10:31:02.000Z')

    configureDatetime({ serverTimezone: 'Asia/Kolkata' })
    expect(parseServerDate('2026-07-25T10:31:02')!.toISOString()).toBe('2026-07-25T05:01:02.000Z')
  })

  it('handles null, empty, Date and epoch inputs', () => {
    expect(parseServerDate(null)).toBeNull()
    expect(parseServerDate('')).toBeNull()
    expect(parseServerDate(0)!.getTime()).toBe(0)
    const d = new Date()
    expect(parseServerDate(d)).toBe(d)
  })
})

describe('formatting in the display zone', () => {
  it('renders UTC instants in the default Asia/Kolkata zone', () => {
    expect(fmtDateTime('2026-07-25T10:31:02Z')).toBe('25 Jul, 16:01')
    expect(fmtDateTimeFull('2026-07-25T10:31:02Z')).toBe('25 Jul 2026, 16:01')
    expect(fmtDate('2026-07-25T10:31:02Z')).toBe('25 Jul 2026')
    expect(fmtTime('2026-07-25T10:31:02Z')).toBe('16:01')
  })

  it('honours a configured display zone', () => {
    configureDatetime({ displayTimezone: 'UTC' })
    expect(fmtDateTime('2026-07-25T10:31:02+05:30')).toBe('25 Jul, 05:01')
  })

  it('falls back to the empty marker', () => {
    expect(fmtDateTime(null)).toBe('—')
    expect(fmtDateTime(undefined, '')).toBe('')
  })
})

describe('relTime', () => {
  it('renders recent instants relatively and old ones absolutely', () => {
    expect(relTime(new Date(Date.now() - 5 * 60_000))).toBe('5m ago')
    expect(relTime(new Date(Date.now() - 3 * 3_600_000))).toBe('3h ago')
    expect(relTime('2026-01-01T00:00:00Z')).toBe('01 Jan, 05:30')
    expect(relTime(null)).toBe('—')
  })
})
