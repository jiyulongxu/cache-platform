{{foreach from=$res item=group}}
        <div class="panel panel-default my-list-panel">
            <div class="my-list-panel-header">
                <a data-toggle="collapse" data-parent="#accordion" href="#{{$group}}" class="list-active" data-group="{{$group}}">
                    <h4 class="panel-title">{{$group}}</h4>
                </a>
            </div>
            <div id="{{$group}}" class="panel-collapse collapse">
                <div class="list-container" id="group-id-{{$group}}">

                </div>
            </div>
        </div>
{{/foreach}}
