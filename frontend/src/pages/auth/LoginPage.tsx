import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Loader2 } from 'lucide-react';
import { toast } from 'sonner';
import AuthLayout from '@/components/layout/AuthLayout';
import { Button } from '@/components/ui/button';
import { Form } from '@/components/ui/form';
import { useAuth } from '@/hooks/useAuth';
import { NotAnOfficialError } from '@/lib/auth';
import { TextField } from '@/components/TextField';
import { getApiErrorData, getApiErrorStatus } from '@/lib/errors';

const loginSchema = z.object({
  email: z.string().email('Valid email required'),
  password: z.string().min(1, 'Password required'),
});

type LoginForm = z.infer<typeof loginSchema>;

export default function LoginPage() {
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const redirectTo = searchParams.get('from') ?? undefined;
  const [isPending, setIsPending] = useState(false);

  const form = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    mode: 'onBlur',
    defaultValues: { email: '', password: '' },
  });

  async function onSubmit(values: LoginForm) {
    setIsPending(true);
    try {
      await login(values.email, values.password, redirectTo);
    } catch (err) {
      // The server answers 403 for a disabled official (#61), and for an account with no official role
      const status = getApiErrorStatus(err);
      if (status === 403 && getApiErrorData(err)?.reason === 'disabled') {
        form.setError('password', { message: 'This account has been disabled. Ask a club admin to enable it.' });
      } else if (err instanceof NotAnOfficialError || status === 403) {
        form.setError('password', { message: 'This sign-in is for race officials only.' });
      } else if (status === 401) {
        form.setError('password', { message: 'Invalid email or password' });
      } else {
        toast.error('Unable to reach server. Please try again.', { duration: 8000 });
      }
    } finally {
      setIsPending(false);
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Race officials: enter your email and password"
      footer={
        <p className="text-sm text-muted-foreground">
          Forgotten your password? Ask your club admin to reset it.
        </p>
      }
    >
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          <TextField control={form.control} name="email" label="Email" type="email" autoComplete="email" />
          <TextField
            control={form.control}
            name="password"
            label="Password"
            type="password"
            autoComplete="current-password"
          />
          <Button type="submit" className="w-full" disabled={isPending}>
            {isPending ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Signing in...
              </>
            ) : (
              'Sign in'
            )}
          </Button>
        </form>
      </Form>
    </AuthLayout>
  );
}
