// Cloudflare Workers expects a default export, which Kotlin/JS can't produce itself.
import { fetch } from "../build/dist/js/productionLibrary/ui-minitel-worker.mjs";

export default { fetch };
