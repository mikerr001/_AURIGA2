# Auriga website deployment

The website is served from `website/web_deploy`; Netlify functions live in `website/netlify/functions`. The repository-root `netlify.toml` configures both paths.

## Automatic GitHub Actions deployment

The workflow `.github/workflows/netlify_deploy.yml` deploys automatically when website files change on `auriga-arael-1` or `main`.

- Pushes to `auriga-arael-1` create a Netlify draft/branch deployment.
- Pushes to `main` create a production deployment.
- Manual deployments can be started from GitHub Actions with **Deploy Auriga website to Netlify**.

Add these repository secrets under **GitHub → Settings → Secrets and variables → Actions**:

1. `NETLIFY_AUTH_TOKEN` — a Netlify personal access token.
2. `NETLIFY_SITE_ID` — the Site ID from the Netlify site's settings.

The current workflow logs confirmed these secrets are not available to the repository yet, so the deployment job cannot authenticate until both are added. Do not put either value in source code or commit them to Git.

## Alternative: Netlify Git integration

You can instead connect this GitHub repository to the existing Netlify site and set the production branch to `main`. Netlify will read the root `netlify.toml` and publish `website/web_deploy`. Enable branch deploys if you also want preview deployments from `auriga-arael-1`.

## Validate a deployment

After the workflow succeeds, open the Netlify deploy summary and verify the published page, `locator.html`, `manifest.json`, and the `/.netlify/functions/submit-feedback` endpoint. A green GitHub build alone does not prove that the deployed site or serverless function is working.
