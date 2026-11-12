import fs from "node:fs";
import path from "node:path";
const dist = path.resolve("dist"),
  entry = path.join(dist, "index.html");
let html = fs.readFileSync(entry, "utf8");
const jsPath = html.match(/<script[^>]*src="([^"]+)"[^>]*><\/script>/)?.[1];
const cssPath = html.match(/<link[^>]*href="([^"]+\.css)"[^>]*>/)?.[1];
if (!jsPath || !cssPath) throw new Error("Mobile build entry not found");
let js = fs.readFileSync(path.join(dist, jsPath.replace(/^\//, "")), "utf8");
for (const name of fs.readdirSync(path.join(dist, "assets"))) {
  if (name.endsWith(".svg")) {
    const data =
      "data:image/svg+xml;base64," +
      fs.readFileSync(path.join(dist, "assets", name)).toString("base64");
    js = js.split("/assets/" + name).join(data);
  }
}
html = html
  .replace(/<script[^>]*src="[^"]+"[^>]*><\/script>/, "")
  .replace(/<link[^>]*href="[^"]+\.css"[^>]*>/, "")
  .replace(/^[\t ]+$/gm, "");
html = html.replace(
  "</head>",
  () =>
    "<style>" +
    fs.readFileSync(path.join(dist, cssPath.replace(/^\//, "")), "utf8") +
    "</style></head>",
);
html = html.replace(
  "</body>",
  () =>
    '<script type="module">' +
    js.replace(/<\/script/gi, "<\\/script") +
    "</script></body>",
);
fs.writeFileSync("차마못한말_현재.html", html);
console.log("Saved: 차마못한말_현재.html");
