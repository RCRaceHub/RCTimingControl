import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2 } from 'lucide-react';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import type { ChampionshipDto } from '@/lib/adminApi';
import { optionalPositiveInt, refineBestXFromY } from './bestXFromY';

const schema = z.object({
  name: z.string().min(1, 'Name is required'),
  bestXFromYX: optionalPositiveInt,
  bestXFromYY: optionalPositiveInt,
  tqBonusPoints: z.coerce.number().int().min(0),
  afinalWinnerBonusPoints: z.coerce.number().int().min(0),
  showOverallTable: z.boolean(),
}).superRefine(refineBestXFromY);

type FormInput = z.input<typeof schema>;
type FormValues = z.output<typeof schema>;

interface Props {
  initialValue?: Partial<ChampionshipDto>;
  onSubmit: (body: Omit<ChampionshipDto, 'id'>) => Promise<unknown>;
  submitLabel: string;
}

export function ChampionshipConfigForm({ initialValue, onSubmit, submitLabel }: Props) {
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormInput, unknown, FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: initialValue?.name ?? '',
      bestXFromYX: initialValue?.bestXFromYX ?? null,
      bestXFromYY: initialValue?.bestXFromYY ?? null,
      tqBonusPoints: initialValue?.tqBonusPoints ?? 0,
      afinalWinnerBonusPoints: initialValue?.afinalWinnerBonusPoints ?? 0,
      showOverallTable: initialValue?.showOverallTable ?? false,
    },
  });

  async function handleFormSubmit(values: FormValues) {
    await onSubmit({
      name: values.name,
      bestXFromYX: values.bestXFromYX,
      bestXFromYY: values.bestXFromYY,
      tqBonusPoints: values.tqBonusPoints,
      afinalWinnerBonusPoints: values.afinalWinnerBonusPoints,
      showOverallTable: values.showOverallTable,
    });
  }

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-5 max-w-md">
      <div className="space-y-1.5">
        <Label htmlFor="champ-name">Name</Label>
        <Input id="champ-name" {...register('name')} placeholder="e.g. 2026 Club Championship" />
        {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
      </div>

      <div className="space-y-1.5">
        <Label>Rounds scoring (optional)</Label>
        <div className="flex items-center gap-2">
          <span className="text-sm text-muted-foreground whitespace-nowrap">Count best</span>
          <Input
            type="number"
            className="w-20"
            placeholder="e.g. 8"
            {...register('bestXFromYX')}
          />
          <span className="text-muted-foreground text-sm">from</span>
          <Input
            type="number"
            className="w-20"
            placeholder="e.g. 10"
            {...register('bestXFromYY')}
          />
          <span className="text-sm text-muted-foreground">rounds</span>
        </div>
        {(errors.bestXFromYX || errors.bestXFromYY) && (
          <p className="text-xs text-destructive">
            {errors.bestXFromYX?.message ?? errors.bestXFromYY?.message ?? 'Must be positive integers if set'}
          </p>
        )}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-1.5">
          <Label htmlFor="tq-bonus">TQ Bonus Points</Label>
          <Input id="tq-bonus" type="number" min={0} {...register('tqBonusPoints')} />
          {errors.tqBonusPoints && (
            <p className="text-xs text-destructive">{errors.tqBonusPoints.message}</p>
          )}
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="afinal-bonus">A-Final Winner Bonus</Label>
          <Input id="afinal-bonus" type="number" min={0} {...register('afinalWinnerBonusPoints')} />
          {errors.afinalWinnerBonusPoints && (
            <p className="text-xs text-destructive">{errors.afinalWinnerBonusPoints.message}</p>
          )}
        </div>
      </div>

      <div className="flex items-start gap-2">
        <input id="overall-table" type="checkbox" className="mt-1" {...register('showOverallTable')} />
        <Label htmlFor="overall-table" className="font-normal">
          Show an overall table that ranks every class together on championship points, as well as each class's own
          table
        </Label>
      </div>

      <Button type="submit" disabled={isSubmitting || (!isDirty && !!initialValue)}>
        {isSubmitting ? <Loader2 className="h-4 w-4 animate-spin mr-1" /> : null}
        {submitLabel}
      </Button>
    </form>
  );
}
