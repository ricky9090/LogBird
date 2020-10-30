var express = require('express');
var path = require('path');
var router = express.Router();


function emitData(req, tag, message) {
  let io = req.app.get('birdlogger');
  if (io !== null) {
    io.emit(tag, message);
  }
}

router.all('/uploadlog', function (req, res, next) {
  var logEntity = req.body;
  //console.log(logEntity);
  //console.log(logEntity.tag);
  //console.log(logEntity.log);
  /*let io = req.app.get('birdlogger');
  if (io !== null) {
    io.emit(logEntity.tag, logEntity.log);
  }*/
  emitData(req, logEntity.tag, logEntity.log);
  res.send("logging success");
});

router.get('/showlog', function(req, res, next) {
  res.sendFile(path.join(__dirname, '../pages', 'showlog.html'));
});

module.exports = router;
