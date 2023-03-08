function OnMult(m_ar, m_br) {
  let pha = Array.from({length: m_ar * m_br}, () => 1.0);
  let phb = Array.from({length: m_br * m_br}, (_, i) => Math.floor(i / m_br) + 1.0);
  let phc = [];

  let tic = performance.now();

  for (let i = 0; i < m_ar; i++) {
    for (let j = 0; j < m_br; j++) {
      let temp = 0;
      for (let k = 0; k < m_ar; k++) {
        temp += pha[i * m_ar + k] * phb[k * m_br + j];
      }
      phc.push(temp);
    }
  }

  let toc = performance.now();

  console.log(`Time: ${(toc - tic) / 1000} seconds`);

  // Display 10 elements of the result matrix to verify correctness
  console.log('Result matrix: ');
  for (let i = 0; i < Math.min(10, phc.length); i++) {
    console.log(phc[i]);
  }
}

function OnMultLine(m_ar, m_br) {
  let pha = Array.from({length: m_ar * m_br}, () => 1.0);
  let phb = Array.from({length: m_br * m_br}, (_, i) => Math.floor(i / m_br) + 1.0);
  let phc = Array.from({length: m_ar * m_br}, () => 0.0);

  let tic = performance.now();

  for (let i = 0; i < m_ar; i++) {
    for (let k = 0; k < m_ar; k++) {
      for (let j = 0; j < m_br; j++) {
        phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
      }
    }
  }

  let toc = performance.now();

  console.log(`Time: ${(toc - tic) / 1000} seconds`);

  // Display 10 elements of the result matrix to verify correctness
  console.log('Result matrix: ');
  for (let i = 0; i < Math.min(10, phc.length); i++) {
    console.log(phc[i]);
  }
}

function OnMultBlock(m_ar, m_br, bkSize) {
  var pha = new Array(m_ar * m_ar).fill(0);
  var phb = new Array(m_ar * m_ar).fill(0);
  var phc = new Array(m_ar * m_ar).fill(0);

  for (var i = 0; i < m_ar; i++) {
      for (var j = 0; j < m_ar; j++) {
          pha[i * m_ar + j] = 1.0;
      }
  }

  for (var i = 0; i < m_br; i++) {
      for (var j = 0; j < m_br; j++) {
          phb[i * m_br + j] = i + 1;
      }
  }

  var n_max = Math.floor(m_ar / bkSize);

  var t1 = Date.now();

  for (var n_i = 0; n_i < n_max; n_i++) {
      for (var n_j = 0; n_j < n_max; n_j++) {
          for (var n_k = 0; n_k < n_max; n_k++) {
              for (var i = n_i * bkSize; i < (n_i * bkSize + bkSize); i++) {
                  for (var k = n_k * bkSize; k < (n_k * bkSize + bkSize); k++) {
                      for (var j = n_j * bkSize; j < (n_j * bkSize + bkSize); j++) {
                          phc[i * m_ar + j] += pha[i * m_ar + k] * phb[k * m_br + j];
                      }
                  }
              }
          }
      }
  }

  var t2 = Date.now();
  console.log("Time: " + (t2 - t1) / 1000 + " seconds");

  // display 10 elements of the result matrix to verify correctness
  console.log("Result matrix: ");
  for (var j = 0; j < Math.min(10, m_br); j++) {
      console.log(phc[j] + " ");
  }

}
