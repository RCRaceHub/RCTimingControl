import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
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
import { adminApi } from '@/lib/adminApi';
import { useClubProfile } from '@/hooks/admin/useAdminClub';
import { adminQueryKeys } from '@/hooks/admin/adminQueryKeys';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';

const TIMEZONES = Intl.supportedValuesOf('timeZone');
const BROWSER_TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;

const schema = z.object({
  name: z.string().min(1, 'Club name is required').max(200),
  timezone: z.string().min(1, 'Timezone is required'),
  email: z.string().email('Valid email required').optional().or(z.literal('')),
  phone: z.string().optional().or(z.literal('')),
  websiteUrl: z.string().url('Valid URL required').optional().or(z.literal('')),
});

type FormValues = z.infer<typeof schema>;

interface Props {
  onNext: () => void;
}

export default function ClubProfileStep({ onNext }: Props) {
  const queryClient = useQueryClient();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      name: '',
      timezone: BROWSER_TZ,
      email: '',
      phone: '',
      websiteUrl: '',
    },
  });

  // Coming back to this step once the club is set up: start from what is saved, not a blank form. The saved
  // position and logo have no field here, so they are sent back as they are rather than cleared.
  const { data: saved } = useClubProfile();
  useEffect(() => {
    if (saved?.name) {
      form.reset({
        name: saved.name,
        timezone: saved.timezone || BROWSER_TZ,
        email: saved.email ?? '',
        phone: saved.phone ?? '',
        websiteUrl: saved.websiteUrl ?? '',
      });
    }
  }, [saved, form]);

  async function onSave(values: FormValues) {
    try {
      await adminApi.club.updateProfile({
        name: values.name,
        email: values.email || null,
        phone: values.phone || null,
        websiteUrl: values.websiteUrl || null,
        latitude: saved?.latitude ?? null,
        longitude: saved?.longitude ?? null,
        timezone: values.timezone,
        logoType: saved?.logoType ?? null,
      });
      queryClient.invalidateQueries({ queryKey: adminQueryKeys.club.profile() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.progress() });
      toast.success('Club profile saved');
      onNext();
    } catch {
      toast.error('Could not save club profile. Try again.');
    }
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Club Profile</h1>
      <p className="text-sm text-muted-foreground mb-6">
        Enter your club's name and contact details. This is the minimum required to use the system.
      </p>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSave)} className="space-y-4">
          <TextField control={form.control} name="name" label="Club Name" placeholder="e.g. Riverside RC Club" />

          <FormField
            control={form.control}
            name="timezone"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Timezone</FormLabel>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select timezone" />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent className="max-h-60">
                    {TIMEZONES.map((tz) => (
                      <SelectItem key={tz} value={tz}>
                        {tz}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <TextField
            control={form.control}
            name="email"
            label="Contact Email (optional)"
            type="email"
            placeholder="club@example.com"
          />

          <TextField
            control={form.control}
            name="phone"
            label="Phone (optional)"
            type="tel"
            placeholder="+44 7700 000000"
          />

          <TextField
            control={form.control}
            name="websiteUrl"
            label="Website URL (optional)"
            type="url"
            placeholder="https://yourclub.example.com"
          />

          <div className="flex justify-end gap-2 pt-4">
            {/* No Back or Skip: the club profile is the minimum the system needs */}
            <Button type="submit" disabled={form.formState.isSubmitting}>
              Save and Continue
            </Button>
          </div>
        </form>
      </Form>
    </div>
  );
}
