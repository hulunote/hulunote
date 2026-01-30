/**
 * Build script for Hulunote Electron app
 * This script copies the compiled ClojureScript files to the electron app directory
 */

const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, '..', 'resources', 'public');
const destDir = path.join(__dirname, 'app');

// Directories to copy
const dirs = ['css', 'html', 'hulunote', 'img'];

function copyRecursive(src, dest) {
  if (!fs.existsSync(src)) {
    console.error(`Source does not exist: ${src}`);
    return;
  }

  const stat = fs.statSync(src);

  if (stat.isDirectory()) {
    if (!fs.existsSync(dest)) {
      fs.mkdirSync(dest, { recursive: true });
    }

    const files = fs.readdirSync(src);
    files.forEach(file => {
      copyRecursive(path.join(src, file), path.join(dest, file));
    });
  } else {
    fs.copyFileSync(src, dest);
    console.log(`Copied: ${src} -> ${dest}`);
  }
}

function clean() {
  if (fs.existsSync(destDir)) {
    fs.rmSync(destDir, { recursive: true });
    console.log('Cleaned app directory');
  }
}

function build() {
  console.log('Building Hulunote Electron app...\n');

  // Clean previous build
  clean();

  // Create app directory
  fs.mkdirSync(destDir, { recursive: true });

  // Copy directories
  dirs.forEach(dir => {
    const src = path.join(srcDir, dir);
    const dest = path.join(destDir, dir);
    
    if (fs.existsSync(src)) {
      copyRecursive(src, dest);
      console.log(`Copied ${dir} directory`);
    } else {
      console.warn(`Warning: ${dir} directory not found at ${src}`);
    }
  });

  // Modify HTML to use relative paths for Electron
  const htmlFile = path.join(destDir, 'html', 'hulunote.html');
  if (fs.existsSync(htmlFile)) {
    let html = fs.readFileSync(htmlFile, 'utf8');
    
    // Change absolute paths to relative paths
    html = html.replace(/href="\/css\//g, 'href="../css/');
    html = html.replace(/src="\/hulunote\//g, 'src="../hulunote/');
    html = html.replace(/src="\/img\//g, 'src="../img/');
    
    fs.writeFileSync(htmlFile, html);
    console.log('Modified HTML paths for Electron');
  }

  console.log('\nBuild complete!');
}

// Run build
build();
