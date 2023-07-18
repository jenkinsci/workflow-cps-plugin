var refDslHlp = {	
  parseDom:function(){
    document.querySelectorAll('dl.root > dt').forEach(function(elem){
      elem.addEventListener('click',refDslHlp.minimizeClick); 
    });
  },
  
  minimizeClick:function(){
    var elem = this;
    var nextElem = elem.nextElementSibling;
    var height = nextElem.offsetHeight;
    if(elem.classList.contains('show-minimize')){
      elem.classList.remove('show-minimize');
      nextElem.classList.remove('minimize');
    }
    else{
      nextElem.style.height = height+'px';
      setTimeout(function(){
        elem.classList.add('show-minimize');
        nextElem.classList.add('minimize');
      },10);
    }    
  }
}

refDslHlp.parseDom();
