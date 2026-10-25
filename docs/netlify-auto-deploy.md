# Netlify automatic deployment

## Current repository behavior

The workflow is `.github/workflows/netlify_deploy.yml`. It runs when website source/configuration changes are pushed to `main` or `auriga-arael-1`, and validates the site before deployment.

- `main` is intended for production deployment.
- `auriga-arael-1` is intended for a branch preview; it does not use the `--prod` flag.
- The published directory is `website/web_deploy`.
- Netlify Functions are in `website/netlify/functions`.
- Do not commit Netlify tokens or site IDs as plaintext.

**Important:** GitHub Actions cannot deploy until the Netlify site credentials are configured. The latest workflow intentionally fails rather than reporting a false success when they are absent.

## Option A — automatic deployment directly from Git (recommended if the site is not connected yet)

1. Open the existing site in the Netlify dashboard.
2. Go to **Project configuration / Site configuration → Build & deploy → Continuous deployment** (labels may vary).
3. Connect the GitHub repository `mikerr001/_AURIGA2`.
4. Set the production branch to `main`.
5. Set the base directory to the repository root, publish directory to `website/web_deploy`, build command to `npm ci --ignore-scripts`, and functions directory to `website/netlify/functions`. The repository's `netlify.toml` already declares these values; keep the dashboard configuration consistent with it.
6. Save, then push a small website change to the connected branch and verify the deployment in Netlify's Deploys page.
7. If you want preview deployments for `auriga-arael-1`, enable branch deploys and select that branch in Netlify's branch-deploy settings.

With Git-based continuous deployment, pushes to the configured branch automatically trigger a new deploy without adding a Netlify token to GitHub Actions.

## Option B — keep GitHub Actions as the deployer

In GitHub, open **Settings → Secrets and variables → Actions → New repository secret** and add:

- `NETLIFY_AUTH_TOKEN`: a Netlify personal access token.
- `NETLIFY_SITE_ID`: the site API ID from the Netlify site configuration.

Do not put either value in source files, commits, issue comments, or screenshots. Then re-run the **Deploy Auriga website to Netlify** workflow. A push changing files under `website/web_deploy/`, `website/netlify/functions/`, `netlify.toml`, or the workflow file will trigger deployment automatically. The workflow deploys `main` as production and `auriga-arael-1` as a non-production deploy.

Choose one deployment owner where possible. Running both Git-based continuous deployment and GitHub Actions can create duplicate deploys. If Git-based deployment is active, disable the Actions deploy workflow or keep it only as a validation workflow.

## Verification checklist

- [ ] A website change pushed to the intended branch starts a deployment.
- [ ] Netlify shows the deployment as published, not merely a successful GitHub validation.
- [ ] The deployed commit matches the pushed commit.
- [ ] `/`, `/demo`, and `/camera` load correctly.
- [ ] Functions deploy and respond as expected.
- [ ] A failed validation or deployment is visible as a failure, never a false green success.
