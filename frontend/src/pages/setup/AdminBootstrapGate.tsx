import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { isAxiosError } from 'axios';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Form } from '@/components/ui/form';
import { bootstrap } from '@/lib/setupApi';
import { useAuth } from '@/hooks/useAuth';
import type { AuthUser } from '@/providers/AuthProvider';
import { setupQueryKeys } from '@/hooks/setup/setupQueryKeys';
import { TextField } from '@/components/TextField';

const bootstrapSchema = z
  .object({
    firstName: z.string().min(1, 'First name required').max(100),
    lastName: z.string().min(1, 'Last name required').max(100),
    email: z.string().email('Valid email required'),
    password: z.string().min(8, 'Password must be at least 8 characters'),
    confirmPassword: z.string().min(1, 'Please confirm your password'),
  })
  .refine((d) => d.password === d.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type BootstrapForm = z.infer<typeof bootstrapSchema>;

export default function AdminBootstrapGate() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { setAuthFromToken } = useAuth();
  const [isPending, setIsPending] = useState(false);

  const form = useForm<BootstrapForm>({
    resolver: zodResolver(bootstrapSchema),
    mode: 'onBlur',
    defaultValues: {
      firstName: '',
      lastName: '',
      email: '',
      password: '',
      confirmPassword: '',
    },
  });

  async function onSubmit(values: BootstrapForm) {
    setIsPending(true);
    try {
      const response = await bootstrap({
        firstName: values.firstName,
        lastName: values.lastName,
        email: values.email,
        password: values.password,
      });

      // Sign the new admin in with the token the bootstrap returned
      const authUser: AuthUser = {
        id: response.userId,
        email: response.email,
        firstName: response.firstName,
        lastName: response.lastName,
        roles: response.roles,
      };
      setAuthFromToken(response.accessToken, authUser);

      // Invalidate setup-status so SetupLayout re-renders into wizard mode
      await queryClient.invalidateQueries({ queryKey: setupQueryKeys.status() });
      navigate('/setup', { replace: true });
    } catch (err) {
      if (isAxiosError(err)) {
        if (err.response?.status === 409) {
          toast.error('Setup is already complete. Please log in instead.');
          navigate('/login');
        } else {
          toast.error('Something went wrong. Check your connection and try again.');
        }
      } else {
        toast.error('Something went wrong. Check your connection and try again.');
      }
    } finally {
      setIsPending(false);
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-background px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="text-2xl font-semibold">Set Up RC Timing</CardTitle>
          <p className="text-sm text-muted-foreground">Create your admin account to get started.</p>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <TextField
                  control={form.control}
                  name="firstName"
                  label="First name"
                  type="text"
                  autoComplete="given-name"
                />
                <TextField
                  control={form.control}
                  name="lastName"
                  label="Last name"
                  type="text"
                  autoComplete="family-name"
                />
              </div>
              <TextField control={form.control} name="email" label="Email" type="email" autoComplete="email" />
              <TextField
                control={form.control}
                name="password"
                label="Password"
                type="password"
                autoComplete="new-password"
              />
              <TextField
                control={form.control}
                name="confirmPassword"
                label="Confirm password"
                type="password"
                autoComplete="new-password"
              />
              <Button type="submit" className="w-full" disabled={isPending}>
                {isPending ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Creating account...
                  </>
                ) : (
                  'Create Admin Account'
                )}
              </Button>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
}
