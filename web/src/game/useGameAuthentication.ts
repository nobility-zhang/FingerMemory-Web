import { ref, type Ref } from 'vue';
import type {
  AccountClient,
  UserView,
} from '../api/client';

type GameAuthenticationApi = Pick<
  AccountClient,
  'loadCurrentUser' | 'login' | 'register'
>;

export type AuthenticationMode = 'login' | 'register';

interface UseGameAuthenticationOptions {
  api: () => GameAuthenticationApi;
  describeError(cause: unknown): string;
}

export function useGameAuthentication({
  api,
  describeError,
}: UseGameAuthenticationOptions): {
  user: Ref<UserView | null>;
  initializing: Ref<boolean>;
  authenticating: Ref<boolean>;
  error: Ref<string | null>;
  mode: Ref<AuthenticationMode>;
  username: Ref<string>;
  displayName: Ref<string>;
  password: Ref<string>;
  initialize(): Promise<UserView | null>;
  submit(): Promise<UserView | null>;
  dispose(): void;
} {
  const user = ref<UserView | null>(null);
  const initializing = ref(true);
  const authenticating = ref(false);
  const error = ref<string | null>(null);
  const mode = ref<AuthenticationMode>('login');
  const username = ref('');
  const displayName = ref('');
  const password = ref('');
  let generation = 0;

  async function initialize(): Promise<UserView | null> {
    const request = ++generation;
    initializing.value = true;
    error.value = null;
    try {
      const loadedUser = await api().loadCurrentUser();
      if (request !== generation) {
        return null;
      }
      user.value = loadedUser;
      return loadedUser;
    } catch (cause) {
      if (request === generation) {
        error.value = describeError(cause);
      }
      return null;
    } finally {
      if (request === generation) {
        initializing.value = false;
      }
    }
  }

  async function submit(): Promise<UserView | null> {
    if (authenticating.value) {
      return null;
    }
    const request = ++generation;
    authenticating.value = true;
    error.value = null;
    try {
      let authenticatedUser: UserView | null;
      if (mode.value === 'register') {
        await api().register(
          username.value,
          password.value,
          displayName.value,
        );
        authenticatedUser = await api().loadCurrentUser();
      } else {
        authenticatedUser = await api().login(
          username.value,
          password.value,
        );
      }
      if (request !== generation) {
        return null;
      }
      if (authenticatedUser === null) {
        throw new Error('登录状态不可用');
      }
      user.value = authenticatedUser;
      password.value = '';
      return authenticatedUser;
    } catch (cause) {
      if (request === generation) {
        error.value = describeError(cause);
      }
      return null;
    } finally {
      if (request === generation) {
        authenticating.value = false;
      }
    }
  }

  function dispose(): void {
    generation += 1;
  }

  return {
    user,
    initializing,
    authenticating,
    error,
    mode,
    username,
    displayName,
    password,
    initialize,
    submit,
    dispose,
  };
}
