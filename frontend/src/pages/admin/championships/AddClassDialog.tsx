import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { useQuery } from '@tanstack/react-query';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { useAddChampionshipClass } from '@/hooks/admin/useAdminChampionships';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import { adminApi } from '@/lib/adminApi';
import { optionalPositiveInt, refineBestXFromY } from './bestXFromY';

const addClassSchema = z.object({
  racingClassId: z.coerce.number().int().positive('Racing class is required'),
  bestXFromYX: optionalPositiveInt,
  bestXFromYY: optionalPositiveInt,
}).superRefine(refineBestXFromY);
type AddClassFormInput = z.input<typeof addClassSchema>;
type AddClassFormValues = z.output<typeof addClassSchema>;

interface AddClassDialogProps {
  championshipId: number;
  open: boolean;
  onOpenChange: (v: boolean) => void;
}

export function AddClassDialog({ championshipId, open, onOpenChange }: AddClassDialogProps) {
  const { data: racingClasses = [], isLoading } = useQuery({
    queryKey: adminQueryKeys.racingClasses.all(),
    queryFn: adminApi.listRacingClasses,
  });
  const addMutation = useAddChampionshipClass(championshipId);
  const { register, handleSubmit, control, reset, formState: { errors, isSubmitting } } =
    useForm<AddClassFormInput, unknown, AddClassFormValues>({ resolver: zodResolver(addClassSchema) });

  async function onSubmit(values: AddClassFormValues) {
    try {
      await addMutation.mutateAsync({
        racingClassId: values.racingClassId,
        bestXFromYX: values.bestXFromYX,
        bestXFromYY: values.bestXFromYY,
      });
      toast.success('Class added');
      reset();
      onOpenChange(false);
    } catch {
      toast.error('Could not add class. Try again.');
    }
  }

  return (
    <Dialog open={open} onOpenChange={v => { onOpenChange(v); if (!v) reset(); }}>
      <DialogContent>
        <DialogHeader><DialogTitle>Add Class</DialogTitle></DialogHeader>
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <div className="space-y-1.5">
            <Label>Racing Class</Label>
            <Controller
              name="racingClassId"
              control={control}
              render={({ field }) => (
                <Select
                  disabled={isLoading}
                  value={field.value ? String(field.value) : ''}
                  onValueChange={v => field.onChange(Number(v))}
                >
                  <SelectTrigger><SelectValue placeholder="Select a class" /></SelectTrigger>
                  <SelectContent>
                    {racingClasses.map(rc => (
                      <SelectItem key={rc.id} value={String(rc.id)}>{rc.name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {errors.racingClassId && <p className="text-xs text-destructive">{errors.racingClassId.message}</p>}
          </div>
          <div className="space-y-1.5">
            <Label>Per-class rounds override (optional)</Label>
            <div className="flex items-center gap-2">
              <span className="text-sm text-muted-foreground whitespace-nowrap">Count best</span>
              <Input type="number" className="w-20" placeholder="e.g. 8" {...register('bestXFromYX')} />
              <span className="text-sm text-muted-foreground">from</span>
              <Input type="number" className="w-20" placeholder="e.g. 10" {...register('bestXFromYY')} />
              <span className="text-sm text-muted-foreground">rounds</span>
            </div>
            {errors.bestXFromYX && (
              <p className="text-xs text-destructive">{errors.bestXFromYX.message}</p>
            )}
          </div>
          <DialogFooter>
            <Button type="submit" disabled={isSubmitting || addMutation.isPending}>
              {addMutation.isPending ? 'Adding…' : 'Add Class'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
