import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { adminApi } from '@/lib/adminApi';
import { adminQueryKeys } from './adminQueryKeys';

/** The built-in features and whether the club has each on (#797). Any official may read it. */
export function useModules() {
  return useQuery({
    queryKey: adminQueryKeys.modules.all(),
    queryFn: adminApi.modules.list,
    staleTime: 15_000,
  });
}

/**
 * The ids of modules the club has switched off, for hiding menu items and routes. Empty until the answer arrives,
 * so nothing flashes hidden while loading or when the request fails.
 */
export function useDisabledModules(): ReadonlySet<string> {
  const { data } = useModules();
  return useMemo(() => new Set((data ?? []).filter(m => !m.clubEnabled).map(m => m.id)), [data]);
}

/** Switches a module on or off for the club (admin). Refreshes every module list. */
export function useSetModule() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) => adminApi.modules.set(id, enabled),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.modules.all() }),
  });
}

/** The modules as one event sees them. */
export function useEventModules(eventId: number) {
  return useQuery({
    queryKey: adminQueryKeys.modules.event(eventId),
    queryFn: () => adminApi.modules.listForEvent(eventId),
  });
}

/** Turns a module off for one event, or back on (race director or admin). */
export function useSetEventModule(eventId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, off }: { id: string; off: boolean }) => adminApi.modules.setForEvent(eventId, id, off),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: adminQueryKeys.modules.all() }),
  });
}
