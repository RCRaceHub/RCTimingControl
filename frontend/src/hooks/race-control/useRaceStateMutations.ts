import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  callGrid,
  startRace,
  stopRace,
  finishRace,
  abandonRace,
  restartRace,
  skipToRace,
  marshalAdjustment,
  raiseIncident,
  applyPenalty,
  recordMarshalAbsent,
  type MarshalAdjustmentRequest,
  type IncidentReportRequest,
  type PenaltyRequest,
  type MarshalAbsenceRequest,
} from '@/lib/raceControlApi';
import { raceControlQueryKeys } from './raceControlQueryKeys';

export function useRaceStateMutations(raceId: number, eventId: number) {
  const queryClient = useQueryClient();

  const invalidateRunOrder = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.runOrder(eventId) });

  // Every command leaves a row in the race's history
  const invalidateHistory = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.raceHistory(raceId) });

  // Calling the grid fixes how the race starts, so the start type and call order are read afresh
  const invalidateReadiness = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.preRaceReadiness(raceId) });

  const invalidateSnapshot = () =>
    queryClient.invalidateQueries({ queryKey: raceControlQueryKeys.resultSnapshot(raceId) });

  return {
    callGrid: useMutation({
      mutationFn: () => callGrid(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateHistory(); invalidateReadiness(); },
    }),
    start: useMutation({
      mutationFn: () => startRace(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateHistory(); },
    }),
    stop: useMutation({
      mutationFn: () => stopRace(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateHistory(); },
    }),
    finish: useMutation({
      mutationFn: () => finishRace(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateSnapshot(); invalidateHistory(); },
    }),
    abandon: useMutation({
      mutationFn: () => abandonRace(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateSnapshot(); invalidateHistory(); },
    }),
    restart: useMutation({
      mutationFn: () => restartRace(raceId),
      onSuccess: () => { invalidateRunOrder(); invalidateSnapshot(); invalidateHistory(); },
    }),
    marshalAdj: useMutation({
      mutationFn: (req: MarshalAdjustmentRequest) => marshalAdjustment(raceId, req),
      onSuccess: invalidateHistory,
    }),
    incident: useMutation({
      mutationFn: (req: IncidentReportRequest) => raiseIncident(raceId, req),
      onSuccess: invalidateHistory,
    }),
    // After the finish a penalty changes the stored result, so a cached copy is stale (#107)
    penalty: useMutation({
      mutationFn: (req: PenaltyRequest) => applyPenalty(raceId, req),
      onSuccess: () => { invalidateSnapshot(); invalidateHistory(); },
    }),
    marshalAbsent: useMutation({
      mutationFn: (req: MarshalAbsenceRequest) => recordMarshalAbsent(raceId, req),
      onSuccess: invalidateHistory,
    }),
    skipTo: useMutation({
      mutationFn: ({ sourceRaceId, targetRaceId }: { sourceRaceId: number; targetRaceId: number }) =>
        skipToRace(sourceRaceId, targetRaceId),
      onSuccess: () => { invalidateRunOrder(); invalidateHistory(); },
    }),
  };
}
