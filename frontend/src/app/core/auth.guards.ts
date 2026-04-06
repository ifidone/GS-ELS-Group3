import { inject, PLATFORM_ID } from '@angular/core';
import { isPlatformServer } from '@angular/common';
import { CanActivateFn, Router } from '@angular/router';
import { Auth } from '@angular/fire/auth';

const waitForAuthReady = async (auth: Auth) => {
  await auth.authStateReady();
  return auth.currentUser;
};

export const authGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (isPlatformServer(platformId)) {
    return true;
  }

  return waitForAuthReady(auth).then((user) =>
    user ? true : router.createUrlTree(['/']),
  );
};

export const publicOnlyGuard: CanActivateFn = () => {
  const auth = inject(Auth);
  const router = inject(Router);
  const platformId = inject(PLATFORM_ID);

  if (isPlatformServer(platformId)) {
    return true;
  }

  return waitForAuthReady(auth).then((user) =>
    user ? router.createUrlTree(['/dashboard']) : true,
  );
};
