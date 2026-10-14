import { useState, useEffect, useRef } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { AlertTriangle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Form,
  FormField,
  FormItem,
  FormLabel,
  FormControl,
  FormMessage,
} from '@/components/ui/form';
import {
  getDecoderConfig,
  testDecoderConfig,
  updateDecoderConfig,
  type DecoderConfigUpdateRequest,
  type DecoderTestResult,
} from '@/lib/setupApi';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';

const PORT_DEFAULTS: Record<string, number> = { RC4: 5100, P3: 5403 };

const schema = z.object({
  decoderHost: z.string().min(1, 'Decoder host required').max(255),
  decoderPort: z.coerce.number().int().min(1).max(65535),
  decoderProtocol: z.enum(['RC4', 'P3']),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  /** Called after the settings are saved. */
  onSaved?: () => void;
  /** Adds a Back button when provided. */
  onBack?: () => void;
  /** Adds a Skip button when provided. */
  onSkip?: () => void;
  saveLabel?: string;
}

/**
 * Decoder host, protocol and port, with Test Connection. Used by the setup wizard and the
 * admin Decoder page. Pre-fills from the saved settings.
 */
export function DecoderSettingsForm({ onSaved, onBack, onSkip, saveLabel = 'Save' }: Props) {
  const queryClient = useQueryClient();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: { decoderHost: '', decoderProtocol: 'RC4', decoderPort: 5100 },
  });

  const userEditedPortRef = useRef(false);
  const watchedProtocol = useWatch({ control: form.control, name: 'decoderProtocol' });

  useEffect(() => {
    if (!userEditedPortRef.current) {
      form.setValue('decoderPort', PORT_DEFAULTS[watchedProtocol], { shouldDirty: false });
    }
  }, [watchedProtocol, form]);

  // Pre-fill from the saved settings. A saved port counts as user-edited only when it differs from
  // the standard port for its protocol. Otherwise switching protocol still moves the port.
  const configQuery = useQuery({
    queryKey: setupQueryKeys.decoderConfig(),
    queryFn: getDecoderConfig,
  });

  useEffect(() => {
    const saved = configQuery.data;
    if (saved?.decoderHost && saved.decoderPort && saved.decoderProtocol) {
      userEditedPortRef.current = saved.decoderPort !== PORT_DEFAULTS[saved.decoderProtocol];
      form.reset({
        decoderHost: saved.decoderHost,
        decoderPort: saved.decoderPort,
        decoderProtocol: saved.decoderProtocol,
      });
    }
  }, [configQuery.data, form]);

  // Tests the values on the form, not the saved listener. The server opens its own connection.
  const [testResult, setTestResult] = useState<DecoderTestResult | null>(null);

  const testMutation = useMutation({
    mutationFn: (req: DecoderConfigUpdateRequest) => testDecoderConfig(req),
    onMutate: () => setTestResult(null),
    onSuccess: (result) => setTestResult(result),
    onError: () => setTestResult({ ok: false, message: 'Could not run the connection test. Try again.' }),
  });

  const onTestConnection = () => {
    const parsed = schema.safeParse(form.getValues());
    if (!parsed.success) {
      void form.trigger();
      return;
    }
    testMutation.mutate(parsed.data);
  };

  async function onSave(values: FormValues) {
    try {
      await updateDecoderConfig(values);
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.decoderConfig() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.progress() });
      toast.success('Decoder configuration saved');
      onSaved?.();
    } catch {
      toast.error('Could not save decoder configuration. Try again.');
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSave)} className="space-y-4">
        <TextField control={form.control} name="decoderHost" label="Decoder Host" placeholder="e.g. 192.168.1.50" />

        <FormField
          control={form.control}
          name="decoderProtocol"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Protocol</FormLabel>
              <Select value={field.value} onValueChange={field.onChange}>
                <FormControl>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  <SelectItem value="RC4">RC4 (firmware &lt; 4.5, port 5100)</SelectItem>
                  {/* Not built yet: the app only reads RC4 text. Listed so a club can see why, but it can't be chosen. */}
                  <SelectItem value="P3" disabled>P3 binary (firmware ≥ 4.5, port 5403) — not supported yet</SelectItem>
                </SelectContent>
              </Select>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="decoderPort"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Port</FormLabel>
              <FormControl>
                <Input
                  type="number"
                  min={1}
                  max={65535}
                  step={1}
                  {...field}
                  onChange={(e) => {
                    userEditedPortRef.current = true;
                    field.onChange(e);
                  }}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-2">
          <Button
            type="button"
            variant="outline"
            onClick={onTestConnection}
            disabled={testMutation.isPending}
          >
            {testMutation.isPending ? 'Testing… (up to 8 s)' : 'Test Connection'}
          </Button>
          {testResult?.ok && (
            <div className="flex items-center gap-2">
              <Badge variant="outline" className="text-[var(--flag-green)] border-[var(--flag-green)]">
                Connected
              </Badge>
              <span className="text-sm text-muted-foreground">{testResult.message}</span>
            </div>
          )}
          {testResult && !testResult.ok && (
            <div className="flex items-start gap-2 p-3 rounded-md bg-muted/50 border">
              <AlertTriangle className="h-4 w-4 shrink-0 mt-0.5 text-muted-foreground" />
              <p className="text-sm">{testResult.message}</p>
            </div>
          )}
        </div>

        <div className="flex justify-between gap-2 pt-4">
          <div>
            {onBack && (
              <Button type="button" variant="ghost" onClick={onBack}>
                Back
              </Button>
            )}
          </div>
          <div className="flex gap-2">
            {onSkip && (
              <Button type="button" variant="ghost" onClick={onSkip}>
                Skip for now
              </Button>
            )}
            <Button type="submit" disabled={form.formState.isSubmitting}>
              {saveLabel}
            </Button>
          </div>
        </div>
      </form>
    </Form>
  );
}
