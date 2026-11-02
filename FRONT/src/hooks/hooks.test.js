import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, cleanup, renderHook, waitFor } from '@testing-library/react';
import { useAutoLoad } from './useAutoLoad';
import { useResettablePage } from './useResettablePage';

afterEach(cleanup);

describe('useAutoLoad', () => {
  it('charge au montage et signale le chargement jusqu’à la réponse', async () => {
    let resolve;
    const load = vi.fn(() => new Promise((r) => { resolve = r; }));

    const { result } = renderHook(() => useAutoLoad(load));

    expect(load).toHaveBeenCalledTimes(1);
    expect(result.current.loading).toBe(true);

    await act(async () => resolve());
    expect(result.current.loading).toBe(false);
  });

  it('recharge quand la fonction change et repasse en chargement', async () => {
    const first = vi.fn(() => Promise.resolve());
    const second = vi.fn(() => new Promise(() => {}));

    const { result, rerender } = renderHook(({ load }) => useAutoLoad(load), { initialProps: { load: first } });
    await waitFor(() => expect(result.current.loading).toBe(false));

    rerender({ load: second });

    expect(second).toHaveBeenCalledTimes(1);
    expect(result.current.loading).toBe(true);
  });

  it('reload relance le chargement et repasse en chargement', async () => {
    const load = vi.fn(() => Promise.resolve());

    const { result } = renderHook(() => useAutoLoad(load));
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => result.current.reload());

    expect(load).toHaveBeenCalledTimes(2);
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
  });

  it('termine le chargement même si la requête échoue', async () => {
    const load = vi.fn(() => Promise.reject(new Error('réseau')).catch(() => {}));

    const { result } = renderHook(() => useAutoLoad(load));

    await waitFor(() => expect(result.current.loading).toBe(false));
  });
});

describe('useResettablePage', () => {
  it('change de page sans toucher à la clé', () => {
    const { result } = renderHook(() => useResettablePage('a'));

    act(() => result.current[1](3));
    expect(result.current[0]).toBe(3);

    act(() => result.current[1]((page) => page + 1));
    expect(result.current[0]).toBe(4);
  });

  it('revient à la page 1 dès que la clé change', () => {
    const { result, rerender } = renderHook(({ key }) => useResettablePage(key), { initialProps: { key: 'a' } });

    act(() => result.current[1](5));
    rerender({ key: 'b' });

    expect(result.current[0]).toBe(1);
  });
});
