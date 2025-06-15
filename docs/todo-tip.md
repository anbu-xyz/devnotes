# Managing Todos

## Endpoints

- http://localhost:8080/addFleetingItem

## Tip to setup a bookmarklet to add fleeting item to the todo list
The following script could be added to the chrome bookmarks toolbar:

```js
javascript:(async function () {
    const myUrl = "http://localhost:8080/addFleetingItem";
    const content = prompt("Enter content to add:");
    if (!content) return;

    function showNotification(message, type = "info", userContent = "") {
        const notification = document.createElement("div");
        notification.style = `
      position: fixed;
      bottom: 20px;
      right: 20px;
      padding: 12px 18px;
      background: ${type === "error" ? "#d9534f" : "#5cb85c"};
      color: #fff;
      font: 14px sans-serif;
      border-radius: 4px;
      box-shadow: 0 2px 6px rgba(0,0,0,0.3);
      z-index: 9999;
      max-width: 300px;
    `;

        if (type === "error") {
            const msg = document.createElement("div");
            msg.textContent = message;

            const textarea = document.createElement("textarea");
            textarea.value = userContent;
            textarea.style = `
        margin-top: 8px;
        width: 100%;
        height: 80px;
        resize: none;
        font-size: 12px;
        font-family: monospace;
        border-radius: 4px;
        border: none;
        padding: 6px;
        color: #000;
        background: #fff;
      `;

            const closeBtn = document.createElement("span");
            closeBtn.textContent = " ×";
            closeBtn.style = `
        margin-left: 10px;
        cursor: pointer;
        font-weight: bold;
        float: right;
      `;
            closeBtn.onclick = () => notification.remove();

            notification.appendChild(msg);
            notification.appendChild(textarea);
            notification.appendChild(closeBtn);
        } else {
            notification.textContent = message;
            setTimeout(() => notification.remove(), 5000);
        }

        document.body.appendChild(notification);
    }

    try {
        const response = await fetch(myUrl, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: content,
            redirect: "manual"
        });

        if (response.status >= 300 && response.status < 400) {
            const location = response.headers.get("Location");
            if (location) {
                window.open(location, "_blank");
                showNotification("Redirecting to content...");
            } else {
                showNotification("Content posted, but no redirect URL.", "error", content);
            }
        } else if (response.status >= 200 && response.status < 300) {
            showNotification("Content posted successfully!");
        } else {
            showNotification("Error: " + response.statusText, "error", content);
        }
    } catch (e) {
        showNotification("Error: " + e.message, "error", content);
    }
})();
```

Or the minified version:

```js
javascript:(async function (){const n=prompt("Enter content to add:");if(n)try{const e=await fetch("http://localhost:8080/addFleetingItem",{method:"POST",headers:{"Content-Type":"application/json"},body:n,redirect:"manual"});if(e.status>=300&&e.status<400){const o=e.headers.get("Location");o?(window.open(o,"_blank"),t("Redirecting to content...")):t("Content posted, but no redirect URL.","error",n)}else e.status>=200&&e.status<300?t("Content posted successfully!"):t("Error: "+e.statusText,"error",n)}catch(e){t("Error: "+e.message,"error",n)}function t(n,t="info",e=""){const o=document.createElement("div");if(o.style=`\n      position: fixed;\n      bottom: 20px;\n      right: 20px;\n      padding: 12px 18px;\n      background: ${"error"===t?"#d9534f":"#5cb85c"};\n      color: #fff;\n      font: 14px sans-serif;\n      border-radius: 4px;\n      box-shadow: 0 2px 6px rgba(0,0,0,0.3);\n      z-index: 9999;\n      max-width: 300px;\n    `,"error"===t){const t=document.createElement("div");t.textContent=n;const r=document.createElement("textarea");r.value=e,r.style="\n        margin-top: 8px;\n        width: 100%;\n        height: 80px;\n        resize: none;\n        font-size: 12px;\n        font-family: monospace;\n        border-radius: 4px;\n        border: none;\n        padding: 6px;\n        color: #000;\n        background: #fff;\n      ";const a=document.createElement("span");a.textContent=" ×",a.style="\n        margin-left: 10px;\n        cursor: pointer;\n        font-weight: bold;\n        float: right;\n      ",a.onclick=()=>o.remove(),o.appendChild(t),o.appendChild(r),o.appendChild(a)}else o.textContent=n,setTimeout((()=>o.remove()),5e3);document.body.appendChild(o)}})();
```

### How to use it

- Create a bookmark in Chrome (right-click bookmarks bar → "Add page").
- Set the name to something like 'Add TODO'.
- Paste the entire code above into the URL field.
- Save it and click it any time you want to run it.
