const fs = require('fs');
const path = require('path');

const storeFile = path.resolve(
  process.env.DATA_FILE || path.join(__dirname, '..', 'data', 'store.json')
);

const emptyStore = {
  tasks: [],
  findings: [],
  feedback: [],
  rules: []
};

function ensureStoreFile() {
  fs.mkdirSync(path.dirname(storeFile), { recursive: true });

  if (!fs.existsSync(storeFile)) {
    fs.writeFileSync(storeFile, JSON.stringify(emptyStore, null, 2));
  }
}

function readStore() {
  ensureStoreFile();

  try {
    return {
      ...emptyStore,
      ...JSON.parse(fs.readFileSync(storeFile, 'utf8'))
    };
  } catch {
    return { ...emptyStore };
  }
}

function writeStore(data) {
  ensureStoreFile();

  const tempFile = storeFile + '.tmp';
  fs.writeFileSync(tempFile, JSON.stringify(data, null, 2));
  fs.renameSync(tempFile, storeFile);
}

function updateStore(mutator) {
  const data = readStore();
  const result = mutator(data);
  writeStore(data);
  return result;
}

module.exports = {
  read: readStore,
  write: writeStore,
  update: updateStore
};
