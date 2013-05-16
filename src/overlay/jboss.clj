(ns overlay.jboss
  (:require [overlay.xml :as xml]))

(defmethod xml/overlay-child :broadcast-group [src tgt]
  (xml/replace-child src tgt))

(defmethod xml/overlay-child :discovery-group [src tgt]
  (xml/replace-child src tgt))
