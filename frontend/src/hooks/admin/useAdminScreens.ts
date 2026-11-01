import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  screenPlaylistsApi,
  screensApi,
  type ControlAction,
  type ScreenPlaylistInput,
} from '@/lib/screensApi';
import { adminQueryKeys } from './adminQueryKeys';

/**
 * Every known screen with its live status. The screens have no push, so the server polls them every few seconds and
 * this reads that result every `refetchInterval` milliseconds (3 s by default).
 */
export function useScreens(options: { refetchInterval?: number | false } = {}) {
  return useQuery({
    queryKey: adminQueryKeys.screens.all(),
    queryFn: screensApi.list,
    refetchInterval: options.refetchInterval ?? 3000,
  });
}

/** The pages of this app, and the addresses the screens can reach it at, for building a playlist. */
export function useBoardPages() {
  return useQuery({ queryKey: adminQueryKeys.screens.boardPages(), queryFn: screensApi.boardPages });
}

/** Every saved playlist. */
export function useScreenPlaylists() {
  return useQuery({ queryKey: adminQueryKeys.screens.playlists(), queryFn: screenPlaylistsApi.list });
}

/** Everything an official can do to screens and playlists; each refreshes what it changed. */
export function useScreenMutations() {
  const qc = useQueryClient();
  const refreshScreens = () => qc.invalidateQueries({ queryKey: adminQueryKeys.screens.all() });
  return {
    add: useMutation({ mutationFn: screensApi.add, onSuccess: refreshScreens }),
    scan: useMutation({ mutationFn: screensApi.scan, onSuccess: refreshScreens }),
    remove: useMutation({ mutationFn: screensApi.remove, onSuccess: refreshScreens }),
    setToken: useMutation({
      mutationFn: ({ ids, token }: { ids: number[]; token: string }) => screensApi.setToken(ids, token),
      onSuccess: refreshScreens,
    }),
    push: useMutation({
      mutationFn: ({ playlistId, ids }: { playlistId: number; ids: number[] }) => screensApi.push(playlistId, ids),
      onSuccess: refreshScreens,
    }),
    repush: useMutation({ mutationFn: screensApi.repush, onSuccess: refreshScreens }),
    control: useMutation({
      mutationFn: ({ action, ids, page }: { action: ControlAction; ids: number[]; page?: number }) =>
        screensApi.control(action, ids, page),
      onSuccess: refreshScreens,
    }),
    show: useMutation({
      mutationFn: (v: { ids: number[]; url: string; seconds: number | null; label: string }) =>
        screensApi.show(v.ids, v.url, v.seconds, v.label),
      onSuccess: refreshScreens,
    }),
    clearShow: useMutation({ mutationFn: screensApi.clearShow, onSuccess: refreshScreens }),
    createPlaylist: useMutation({
      mutationFn: (b: ScreenPlaylistInput) => screenPlaylistsApi.create(b),
      onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.screens.playlists() }),
    }),
    updatePlaylist: useMutation({
      mutationFn: ({ id, ...b }: ScreenPlaylistInput & { id: number }) => screenPlaylistsApi.update(id, b),
      onSuccess: () => qc.invalidateQueries({ queryKey: adminQueryKeys.screens.playlists() }),
    }),
    deletePlaylist: useMutation({
      mutationFn: screenPlaylistsApi.delete,
      onSuccess: () => {
        void qc.invalidateQueries({ queryKey: adminQueryKeys.screens.playlists() });
        return refreshScreens();
      },
    }),
  };
}
