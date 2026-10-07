export async function readTextDocument(file:File):Promise<{title:string;content:string}> {
  if(!/\.(txt|md)$/i.test(file.name))throw new Error('지금은 .txt와 .md 파일을 읽을 수 있어요. 다른 문서는 필요한 내용을 복사해 넣어 주세요.')
  if(file.size>80000)throw new Error('80KB 이하의 텍스트 파일을 골라 주세요.')
  const content=await file.text()
  if(!content.trim())throw new Error('파일에 내용이 없어요.')
  if(content.includes('\u0000')||content.includes('\uFFFD'))throw new Error('UTF-8 텍스트 파일인지 확인해 주세요.')
  if(content.length>20000)throw new Error('문서 한 개에 20,000자까지 넣을 수 있어요. 내용을 나눠 주세요.')
  return {title:file.name.replace(/\.(txt|md)$/i,'').slice(0,160),content}
}
