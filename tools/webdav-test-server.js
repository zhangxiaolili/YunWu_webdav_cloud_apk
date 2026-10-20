/*
 * Minimal WebDAV server used only for local end-to-end testing of CloudDock.
 * Run: node tools/webdav-test-server.js
 * Emulator URL: http://10.0.2.2:8080/dav/
 * Credentials: demo / demo123
 */
const http = require('http');
const fs = require('fs');
const fsp = require('fs/promises');
const path = require('path');
const crypto = require('crypto');

const PORT = 8080;
const BASE = '/dav';
const ROOT = path.resolve(__dirname, 'webdav-root');
const AUTH = 'Basic ' + Buffer.from('demo:demo123').toString('base64');

function xmlEscape(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;');
}

function hrefFor(relative, isDirectory) {
  const segments = relative.split('/').filter(Boolean).map(encodeURIComponent);
  let href = BASE + '/' + segments.join('/');
  if (isDirectory && !href.endsWith('/')) href += '/';
  if (href === BASE) href += '/';
  return href;
}

function urlPathToRelative(rawUrl) {
  const parsed = new URL(rawUrl, 'http://localhost');
  let pathname = decodeURIComponent(parsed.pathname);
  if (!pathname.startsWith(BASE)) return null;
  let relative = pathname.slice(BASE.length).replace(/^\/+/, '').replace(/\/+$/, '');
  if (relative.split('/').some((item) => item === '..')) return null;
  return relative;
}

function absoluteFor(relative) {
  const result = path.resolve(ROOT, relative);
  if (result !== ROOT && !result.startsWith(ROOT + path.sep)) return null;
  return result;
}

function etag(stat) {
  return '"' + crypto.createHash('md5').update(`${stat.size}:${stat.mtimeMs}`).digest('hex') + '"';
}

function mimeFor(name) {
  const ext = path.extname(name).toLowerCase();
  return {
    '.txt': 'text/plain; charset=utf-8',
    '.md': 'text/markdown; charset=utf-8',
    '.kt': 'text/x-kotlin; charset=utf-8',
    '.json': 'application/json',
    '.svg': 'image/svg+xml',
    '.png': 'image/png',
    '.wav': 'audio/wav',
    '.mp4': 'video/mp4'
  }[ext] || 'application/octet-stream';
}

async function resourceXml(relative) {
  const absolute = absoluteFor(relative);
  if (!absolute) return null;
  const stat = await fsp.stat(absolute);
  const isDirectory = stat.isDirectory();
  const name = relative ? path.basename(relative) : 'dav';
  const href = hrefFor(relative, isDirectory);
  const props = [
    `<D:displayname>${xmlEscape(name)}</D:displayname>`,
    `<D:getlastmodified>${stat.mtime.toUTCString()}</D:getlastmodified>`,
    `<D:getetag>${xmlEscape(etag(stat))}</D:getetag>`,
    isDirectory
      ? '<D:resourcetype><D:collection/></D:resourcetype>'
      : '<D:resourcetype/>'
  ];
  if (!isDirectory) {
    props.push(`<D:getcontentlength>${stat.size}</D:getcontentlength>`);
    props.push(`<D:getcontenttype>${xmlEscape(mimeFor(name))}</D:getcontenttype>`);
  }
  return `<D:response><D:href>${xmlEscape(href)}</D:href><D:propstat><D:prop>${props.join('')}</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat></D:response>`;
}

async function ensureSeedData() {
  await fsp.mkdir(ROOT, { recursive: true });
  const files = {
    'README.md': `# 云坞测试空间

这是一个用于端到端测试的 **WebDAV** 文件。

## 功能清单

- 多账户与侧边栏挂载
- SAF 本地缓存目录
- 文件浏览、重命名、上传、下载
- 非破坏性双向同步
- Markdown 预览与代码高亮

## 代码示例

\`\`\`kotlin
fun hello(name: String): String {
    return "你好，\$name"
}
\`\`\`

> 修改这个文件，然后观察本地缓存与云端同步。

| 项目 | 状态 |
| --- | --- |
| 文本预览 | 完成 |
| 音频播放 | 完成 |
| 视频播放 | 完成 |

更多信息见 [Kotlin 官网](https://kotlinlang.org)。
`,
    'notes/中文笔记.txt': '你好，云坞！\n\n这是 UTF-8 编码的中文文本。\n第二行内容。\n',
    'notes/spaces in name.txt': 'A file name with spaces.\n',
    'code/Main.kt': `package demo

import kotlinx.coroutines.flow.Flow

data class FileItem(val name: String, val size: Long)

fun main() {
    val files = listOf(
        FileItem("README.md", 2048),
        FileItem("cover.png", 81920)
    )
    files.sortedBy { it.name }.forEach { println("\${it.name}: \${it.size}") }
}
`,
    'code/config.json': '{\n  "name": "CloudDock",\n  "version": "1.0.0",\n  "sync": true,\n  "extensions": ["md", "txt", "kt"]\n}\n',
    'images/cover.svg': `<svg xmlns="http://www.w3.org/2000/svg" width="960" height="640" viewBox="0 0 960 640">
  <defs>
    <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#4F63E8"/>
      <stop offset="1" stop-color="#0D8F83"/>
    </linearGradient>
  </defs>
  <rect width="960" height="640" fill="url(#g)"/>
  <circle cx="720" cy="180" r="190" fill="#ffffff" opacity="0.10"/>
  <path d="M330 350a78 78 0 0 1 13-155 112 112 0 0 1 211 32 79 79 0 0 1-20 156H330z" fill="#ffffff" opacity="0.94"/>
  <text x="480" y="530" font-family="sans-serif" font-size="48" font-weight="bold" fill="#ffffff" text-anchor="middle">CloudDock</text>
</svg>
`,
    'notes/代码片段.js': `// Syntax highlighting test
const items = [1, 2, 3, 4];
function total(values) {
  return values.reduce((sum, value) => sum + value, 0);
}
console.log("total:", total(items));
`,
    'folder/subfolder/nested.txt': 'A nested file used for download-folder tests.\n',
    'folder/empty/.keep': ''
  };
  for (const [relative, content] of Object.entries(files)) {
    const absolute = path.join(ROOT, relative);
    await fsp.mkdir(path.dirname(absolute), { recursive: true });
    try {
      await fsp.access(absolute);
    } catch {
      await fsp.writeFile(absolute, content, 'utf8');
    }
  }
  await fsp.mkdir(path.join(ROOT, 'empty-folder'), { recursive: true });
  const wave = path.join(ROOT, 'media/tone.wav');
  if (!fs.existsSync(wave)) {
    await fsp.mkdir(path.dirname(wave), { recursive: true });
    const sampleRate = 44100;
    const seconds = 3;
    const samples = sampleRate * seconds;
    const dataSize = samples * 2;
    const buffer = Buffer.alloc(44 + dataSize);
    buffer.write('RIFF', 0);
    buffer.writeUInt32LE(36 + dataSize, 4);
    buffer.write('WAVE', 8);
    buffer.write('fmt ', 12);
    buffer.writeUInt32LE(16, 16);
    buffer.writeUInt16LE(1, 20);
    buffer.writeUInt16LE(1, 22);
    buffer.writeUInt32LE(sampleRate, 24);
    buffer.writeUInt32LE(sampleRate * 2, 28);
    buffer.writeUInt16LE(2, 32);
    buffer.writeUInt16LE(16, 34);
    buffer.write('data', 36);
    buffer.writeUInt32LE(dataSize, 40);
    for (let i = 0; i < samples; i++) {
      const t = i / sampleRate;
      const freq = t < 1.5 ? 440 : 554.37;
      const envelope = Math.min(1, t * 8) * Math.min(1, (seconds - t) * 8);
      const value = Math.round(Math.sin(2 * Math.PI * freq * t) * 0.28 * envelope * 32767);
      buffer.writeInt16LE(value, 44 + i * 2);
    }
    await fsp.writeFile(wave, buffer);
  }
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    request.on('data', (chunk) => chunks.push(chunk));
    request.on('end', () => resolve(Buffer.concat(chunks)));
    request.on('error', reject);
  });
}

async function handle(request, response) {
  if (request.headers.authorization !== AUTH) {
    response.writeHead(401, {
      'WWW-Authenticate': 'Basic realm="CloudDock Test"',
      'Content-Type': 'text/plain; charset=utf-8'
    });
    response.end('Authentication required');
    return;
  }

  const relative = urlPathToRelative(request.url);
  if (relative === null) {
    response.writeHead(400);
    response.end('Bad path');
    return;
  }
  const absolute = absoluteFor(relative);
  if (!absolute) {
    response.writeHead(400);
    response.end('Bad path');
    return;
  }

  const method = request.method || 'GET';
  if (method === 'OPTIONS') {
    response.writeHead(200, {
      DAV: '1, 2',
      Allow: 'OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE'
    });
    response.end();
    return;
  }

  if (method === 'PROPFIND') {
    try {
      const stat = await fsp.stat(absolute);
      const depth = request.headers.depth === '0' ? 0 : 1;
      const entries = [await resourceXml(relative)];
      if (depth === 1 && stat.isDirectory()) {
        const children = await fsp.readdir(absolute, { withFileTypes: true });
        for (const child of children) {
          const childRelative = relative ? `${relative}/${child.name}` : child.name;
          entries.push(await resourceXml(childRelative));
        }
      }
      const body = `<?xml version="1.0" encoding="utf-8"?><D:multistatus xmlns:D="DAV:">${entries.filter(Boolean).join('')}</D:multistatus>`;
      response.writeHead(207, {
        'Content-Type': 'application/xml; charset=utf-8',
        'DAV': '1, 2'
      });
      response.end(body);
    } catch (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500);
      response.end(error.message);
    }
    return;
  }

  if (method === 'GET' || method === 'HEAD') {
    try {
      const stat = await fsp.stat(absolute);
      if (stat.isDirectory()) {
        response.writeHead(405);
        response.end('Directory');
        return;
      }
      response.writeHead(200, {
        'Content-Type': mimeFor(path.basename(absolute)),
        'Content-Length': stat.size,
        'Last-Modified': stat.mtime.toUTCString(),
        'ETag': etag(stat)
      });
      if (method === 'HEAD') {
        response.end();
      } else {
        fs.createReadStream(absolute).pipe(response);
      }
    } catch (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500);
      response.end(error.message);
    }
    return;
  }

  if (method === 'PUT') {
    try {
      const body = await readBody(request);
      await fsp.mkdir(path.dirname(absolute), { recursive: true });
      await fsp.writeFile(absolute, body);
      response.writeHead(201, { ETag: etag(await fsp.stat(absolute)) });
      response.end();
    } catch (error) {
      response.writeHead(500);
      response.end(error.message);
    }
    return;
  }

  if (method === 'MKCOL') {
    try {
      await fsp.mkdir(absolute);
      response.writeHead(201);
      response.end();
    } catch (error) {
      response.writeHead(error.code === 'EEXIST' ? 405 : 409);
      response.end(error.message);
    }
    return;
  }

  if (method === 'DELETE') {
    try {
      await fsp.rm(absolute, { recursive: true, force: false });
      response.writeHead(204);
      response.end();
    } catch (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500);
      response.end(error.message);
    }
    return;
  }

  if (method === 'MOVE') {
    try {
      const destinationHeader = request.headers.destination;
      if (!destinationHeader) {
        response.writeHead(400);
        response.end('Missing Destination');
        return;
      }
      const destinationRelative = urlPathToRelative(destinationHeader);
      const destination = destinationRelative === null ? null : absoluteFor(destinationRelative);
      if (!destination) {
        response.writeHead(400);
        response.end('Bad Destination');
        return;
      }
      await fsp.mkdir(path.dirname(destination), { recursive: true });
      if (request.headers.overwrite !== 'F') {
        await fsp.rm(destination, { recursive: true, force: true });
      }
      await fsp.rename(absolute, destination);
      response.writeHead(201);
      response.end();
    } catch (error) {
      response.writeHead(error.code === 'ENOENT' ? 404 : 500);
      response.end(error.message);
    }
    return;
  }

  response.writeHead(405, { Allow: 'OPTIONS, PROPFIND, GET, HEAD, PUT, DELETE, MKCOL, MOVE' });
  response.end('Method not allowed');
}

ensureSeedData()
  .then(() => {
    http.createServer((request, response) => {
      handle(request, response).catch((error) => {
        console.error(error);
        if (!response.headersSent) response.writeHead(500);
        response.end('Internal error');
      });
    }).listen(PORT, '0.0.0.0', () => {
      console.log(`CloudDock WebDAV test server listening on http://0.0.0.0:${PORT}${BASE}/`);
      console.log('Credentials: demo / demo123');
    });
  })
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
