import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { createSetupStaff } from '@/lib/setupApi';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';
import { OfficialFields } from '@/components/OfficialFields';
import { officialSchema, PASSWORDS_DIFFER } from '@/lib/officials';
import { useRecheckConfirmation } from '@/hooks/useRecheckConfirmation';
import { getApiErrorMessage, getApiErrorStatus } from '@/lib/errors';

const schema = officialSchema
  .extend({ confirmPassword: z.string().min(1, 'Please confirm your password') })
  .refine((d) => d.password === d.confirmPassword, {
    message: PASSWORDS_DIFFER,
    path: ['confirmPassword'],
  });

type FormValues = z.infer<typeof schema>;

interface Props {
  onNext: () => void;
  onBack?: () => void;
}

export default function StaffStep({ onNext, onBack }: Props) {
  const queryClient = useQueryClient();

  const form = useForm<FormValues>({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
      roles: [],
    },
  });
  useRecheckConfirmation(form, 'password', 'confirmPassword');

  async function onSave(values: FormValues) {
    try {
      await createSetupStaff({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
        roles: values.roles,
      });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      queryClient.invalidateQueries({ queryKey: setupQueryKeys.progress() });
      toast.success('Staff account created');
      onNext();
    } catch (err) {
      if (getApiErrorStatus(err) === 409) {
        form.setError('email', {
          message: 'An account with this email already exists.',
        });
      } else {
        toast.error(getApiErrorMessage(err, 'Could not create staff account. Try again.'));
      }
    }
  }

  function onSkip() {
    onNext();
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold mb-2">Staff Account</h1>
      <p className="text-sm text-muted-foreground mb-6">
        Add at least one staff account. Assign roles to control what each person can access.
      </p>

      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSave)} className="space-y-4">
          <OfficialFields
            control={form.control}
            rolesIdPrefix="staff-role"
            afterPassword={
              <TextField
                control={form.control}
                name="confirmPassword"
                label="Confirm password"
                type="password"
                autoComplete="new-password"
              />
            }
          />

          <div className="flex justify-between gap-2 pt-4">
            <Button type="button" variant="ghost" onClick={onBack}>
              Back
            </Button>
            <div className="flex gap-2">
              <Button type="button" variant="ghost" onClick={onSkip}>
                Skip for now
              </Button>
              <Button type="submit" disabled={form.formState.isSubmitting}>
                Save and Continue
              </Button>
            </div>
          </div>
        </form>
      </Form>
    </div>
  );
}
