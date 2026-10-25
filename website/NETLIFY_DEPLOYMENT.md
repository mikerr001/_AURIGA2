# Auriga website deployment

The public website source is `website/web_deploy/`. Netlify configuration lives in the repository-root `netlify.toml`; serverless feedback handling lives in `website/netlify/functions/`.

## Automatic checks

The `.github/workflows/netlify_deploy.yml` workflow runs on pushes that change the website, Netlify function, package files, or Netlify configuration. It checks the three main pages, local asset references, the web manifest, and JavaScript syntax before attempting a deployment.

## Enable automatic deploys from GitHub Actions

Add these two repository Actions secrets:

- `NETLIFY_AUTH_TOKEN`: a Netlify personal access token.
- `NETLIFY_SITE_ID`: the site ID for the intended Auriga Netlify site.

Go to **GitHub → Settings → Secrets and variables → Actions → New repository secret** for each value. Do not put either value in source code or commit it.

After that:
- A push to `main` deploys production.
- A push to `auriga-arael-1` deploys a branch preview (not production).
- A pull request runs validation only.

If the secrets are absent, validation still runs and the deployment step warns and skips rather than making every CI run fail.

## Alternative: Netlify Git integration

You can instead connect this GitHub repository in Netlify and enable deploys for the chosen branch. Netlify's own Git integration then deploys automatically without Actions secrets. Keep the publish directory set to `website/web_deploy` and the functions directory set to `website/netlify/functions`.

## Feedback delivery

The website can submit feedback to `/.netlify/functions/submit-feedback`. To enable email notifications, configure `GMAIL_USER` and `GMAIL_APP_PASSWORD` in the Netlify site's environment variables. GitHub issue mirroring is optional and requires `FEEDBACK_GITHUB_REPO` plus `FEEDBACK_GITHUB_TOKEN`. Without a configured feedback sink, the website falls back to the user's email app.
